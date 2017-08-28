#!/usr/bin/python

# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Command line tool to analyze wave file and detect artifacts."""

import argparse
import logging
import math
import numpy
import pprint
import wave

import common
from autotest_lib.client.cros.audio import audio_analysis
from autotest_lib.client.cros.audio import audio_data
from autotest_lib.client.cros.audio import audio_quality_measurement


def add_args(parser):
    """Adds command line arguments."""
    parser.add_argument('filename', metavar='WAV_FILE', type=str,
                        help='The wave file to check.')
    parser.add_argument('--debug', action='store_true', default=False,
                        help='Show debug message.')
    parser.add_argument('--spectral', action='store_true', default=False,
                        help='Spectral analysis on each channel.')
    parser.add_argument('--quality', action='store_true', default=False,
                        help='Quality analysis on each channel. Implies '
                             '--spectral')


def parse_args(parser):
    """Parses args."""
    args = parser.parse_args()
    # Quality is checked within spectral analysis.
    if args.quality:
        args.spectral = True
    return args


class WaveFileException(Exception):
    """Error in WaveFile."""
    pass


class WaveFile(object):
    """Class which handles wave file reading.

    Properties:
        raw_data: audio_data.AudioRawData object for data in wave file.
        rate: sampling rate.

    """
    def __init__(self, filename):
        """Inits a wave file.

        @param filename: file name of the wave file.

        """
        self.raw_data = None
        self.rate = None

        self._filename = filename
        self._wave_reader = None
        self._n_channels = None
        self._sample_width_bits = None
        self._n_frames = None
        self._binary = None

        self._read_wave_file()


    def _read_wave_file(self):
        """Reads wave file header and samples.

        @raises:
            WaveFileException: Wave format is not supported.

        """
        try:
            self._wave_reader = wave.open(self._filename, 'r')
            self._read_wave_header()
            self._read_wave_binary()
        except wave.Error as e:
            if 'unknown format: 65534' in str(e):
                raise WaveFileException(
                        'WAVE_FORMAT_EXTENSIBLE is not supproted. '
                        'Try command "sox in.wav -t wavpcm out.wav" to convert '
                        'the file to WAVE_FORMAT_PCM format.')
        finally:
            if self._wave_reader:
                self._wave_reader.close()


    def _read_wave_header(self):
        """Reads wave file header.

        @raises WaveFileException: wave file is compressed.

        """
        # Header is a tuple of
        # (nchannels, sampwidth, framerate, nframes, comptype, compname).
        header = self._wave_reader.getparams()
        logging.debug('Wave header: %s', header)

        self._n_channels = header[0]
        self._sample_width_bits = header[1] * 8
        self.rate = header[2]
        self._n_frames = header[3]
        comptype = header[4]
        compname = header[5]

        if comptype != 'NONE' or compname != 'not compressed':
            raise WaveFileException('Can not support compressed wav file.')


    def _read_wave_binary(self):
        """Reads in samples in wave file."""
        self._binary = self._wave_reader.readframes(self._n_frames)
        format_str = 'S%d_LE' % self._sample_width_bits
        self.raw_data = audio_data.AudioRawData(
                binary=self._binary,
                channel=self._n_channels,
                sample_format=format_str)


class QualityCheckerError(Exception):
    """Error in QualityChecker."""
    pass


class QualityChecker(object):
    """Quality checker controls the flow of checking quality of raw data."""
    def __init__(self, raw_data, rate):
        """Inits a quality checker.

        @param raw_data: An audio_data.AudioRawData object.
        @param rate: Sampling rate.

        """
        self._raw_data = raw_data
        self._rate = rate


    def do_spectral_analysis(self, check_quality=False):
        """Gets the spectral_analysis result.

        @param check_quality: Check quality of each channel.

        """
        self.has_data()
        for channel_idx in xrange(self._raw_data.channel):
            signal = self._raw_data.channel_data[channel_idx]
            max_abs = max(numpy.abs(signal))
            logging.debug('Channel %d max abs signal: %f', channel_idx, max_abs)
            if max_abs == 0:
                logging.info('No data on channel %d, skip this channel',
                              channel_idx)
                continue

            saturate_value = audio_data.get_maximum_value_from_sample_format(
                    self._raw_data.sample_format)
            normalized_signal = audio_analysis.normalize_signal(
                    signal, saturate_value)
            logging.debug('saturate_value: %f', saturate_value)
            logging.debug('max signal after normalized: %f', max(normalized_signal))
            spectral = audio_analysis.spectral_analysis(
                    normalized_signal, self._rate)
            logging.info('Channel %d spectral:\n%s', channel_idx,
                         pprint.pformat(spectral))

            if check_quality:
                quality = audio_quality_measurement.quality_measurement(
                        signal=normalized_signal,
                        rate=self._rate,
                        dominant_frequency=spectral[0][0])
                logging.info('Channel %d quality:\n%s', channel_idx,
                             pprint.pformat(quality))


    def has_data(self):
        """Checks if data has been set.

        @raises QualityCheckerError: if data or rate is not set yet.

        """
        if not self._raw_data or not self._rate:
            raise QualityCheckerError('Data and rate is not set yet')


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description='Check signal quality of a wave file. Each channel should'
                    ' either be all zeros, or sine wave of a fixed frequency.')
    add_args(parser)
    args = parse_args(parser)

    level = logging.DEBUG if args.debug else logging.INFO
    format = '%(asctime)-15s:%(levelname)s:%(pathname)s:%(lineno)d: %(message)s'
    logging.basicConfig(format=format, level=level)

    wavefile = WaveFile(args.filename)

    checker = QualityChecker(wavefile.raw_data, wavefile.rate)

    if args.spectral:
        checker.do_spectral_analysis(check_quality=args.quality)
