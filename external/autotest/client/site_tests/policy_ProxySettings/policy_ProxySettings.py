# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging, threading

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.enterprise import enterprise_policy_base
from SocketServer import ThreadingTCPServer, StreamRequestHandler


class ProxyHandler(StreamRequestHandler):
    """Provide request handler for the Threaded Proxy Listener."""

    def handle(self):
        """Get URL of request from first line.

        Read the first line of the request, up to 40 characters, and look
        for the URL of the request. If found, save it to the URL list.

        Note: All requests are sent an HTTP 504 error.
        """
        # Capture URL in first 40 chars of request.
        data = self.rfile.readline(40).strip()
        logging.debug('ProxyHandler::handle(): <%s>', data)
        self.server.store_requests_received(data)
        self.wfile.write('HTTP/1.1 504 Gateway Timeout\r\n'
                         'Connection: close\r\n\r\n')


class ThreadedProxyServer(ThreadingTCPServer):
    """Provide a Threaded Proxy Server to service and save requests.

    Define a Threaded Proxy Server which services requests, and allows the
    handler to save all requests.
    """

    def __init__(self, server_address, HandlerClass):
        """Constructor.

        @param server_address: tuple of server IP and port to listen on.
        @param HandlerClass: the RequestHandler class to instantiate per req.
        """
        self.requests_received = []
        ThreadingTCPServer.allow_reuse_address = True
        ThreadingTCPServer.__init__(self, server_address, HandlerClass)

    def store_requests_received(self, request):
        """Add receieved request to list.

        @param request: request received by the proxy server.
        """
        self.requests_received.append(request)


class ProxyListener(object):
    """Provide a Proxy Listener to detect connect requests.

    Define a proxy listener to detect when a CONNECT request is seen at the
    given |server_address|, and record all requests received. Requests
    received are exposed to the caller.
    """

    def __init__(self, server_address):
        """Constructor.

        @param server_address: tuple of server IP and port to listen on.
        """
        self._server = ThreadedProxyServer(server_address, ProxyHandler)
        self._thread = threading.Thread(target=self._server.serve_forever)

    def run(self):
        """Start the server by activating it's thread."""
        self._thread.start()

    def stop(self):
        """Stop the server and its threads."""
        self._server.server_close()
        self._thread.join()

    def get_requests_received(self):
        """Get list of received requests."""
        return self._server.requests_received

    def reset_requests_received(self):
        """Clear list of received requests."""
        self._server.requests_received = []


class policy_ProxySettings(enterprise_policy_base.EnterprisePolicyTest):
    """Test effect of ProxySettings policy on Chrome OS behavior.

    This test verifies the behavior of Chrome OS for specific configurations
    of the ProxySettings use policy: None (undefined), ProxyMode=direct,
    ProxyMode=fixed_servers, ProxyMode=pac_script. None means that the policy
    value is not set. This induces the default behavior, equivalent to what is
    seen by an un-managed user.

    When ProxySettings is None (undefined) or ProxyMode=direct, then no proxy
    server should be used. When ProxyMode=fixed_servers or pac_script, then
    the proxy server address specified by the ProxyServer or ProxyPacUrl
    entry should be used.
    """
    version = 1

    def initialize(self, **kwargs):
        self._initialize_test_constants()
        super(policy_ProxySettings, self).initialize(**kwargs)
        self._proxy_server = ProxyListener(('', self.PROXY_PORT))
        self._proxy_server.run()
        self.start_webserver()


    def _initialize_test_constants(self):
        """Initialize test-specific constants, some from class constants."""
        self.POLICY_NAME = 'ProxySettings'
        self.PROXY_PORT = 3128
        self.PAC_FILE = 'proxy_test.pac'
        self.FIXED_PROXY = {
            'ProxyBypassList': 'www.google.com,www.googleapis.com',
            'ProxyMode': 'fixed_servers',
            'ProxyServer': 'localhost:%s' % self.PROXY_PORT
        }
        self.PAC_PROXY = {
            'ProxyMode': 'pac_script',
            'ProxyPacUrl': '%s/%s' % (self.WEB_HOST, self.PAC_FILE)
        }
        self.DIRECT_PROXY = {
            'ProxyMode': 'direct'
        }
        self.TEST_URL = 'http://www.wired.com/'
        self.TEST_CASES = {
            'FixedProxy_UseFixedProxy': self.FIXED_PROXY,
            'PacProxy_UsePacFile': self.PAC_PROXY,
            'DirectProxy_UseNoProxy': self.DIRECT_PROXY,
            'NotSet_UseNoProxy': None
        }


    def cleanup(self):
        self._proxy_server.stop()
        super(policy_ProxySettings, self).cleanup()


    def _test_proxy_configuration(self, policy_value):
        """Verify CrOS enforces the specified ProxySettings configuration.

        @param policy_value: policy value expected.
        """
        self._proxy_server.reset_requests_received()
        self.navigate_to_url(self.TEST_URL)
        proxied_requests = self._proxy_server.get_requests_received()

        # Determine whether TEST_URL is in |proxied_requests|. Comprehension
        # is conceptually equivalent to `TEST_URL in proxied_requests`;
        # however, we must do partial matching since TEST_URL and the
        # elements inside |proxied_requests| are not necessarily equal, i.e.,
        # TEST_URL is a substring of the received request.
        matching_requests = [request for request in proxied_requests
                             if self.TEST_URL in request]
        logging.info('matching_requests: %s', matching_requests)

        mode = policy_value['ProxyMode'] if policy_value else None
        if mode is None or mode == 'direct':
            if matching_requests:
                raise error.TestFail('Requests should not have been sent '
                                     'through the proxy server.')
        elif mode == 'fixed_servers' or mode == 'pac_script':
            if not matching_requests:
                raise error.TestFail('Requests should have been sent '
                                     'through the proxy server.')
        else:
            raise error.TestFail('Unrecognized Policy Value %s', policy_value)


    def run_test_case(self, case):
        """Setup and run the test configured for the specified test case.

        @param case: Name of the test case to run.
        """
        case_value = self.TEST_CASES[case]
        self.setup_case(self.POLICY_NAME, case_value)
        self._test_proxy_configuration(case_value)
