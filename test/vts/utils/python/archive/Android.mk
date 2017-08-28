# Copyright 2016 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

PARSER := parser_test.py

GCNO_PARSER := gcno_parser_test.py

GCDA_PARSER := gcda_parser_test.py

ARC_SUMMARY := arc_summary_test.py

FUNCTION_SUMMARY := function_summary_test.py

COVERAGE_REPORT := coverage_report_test.py

default:
	@echo "Running unit test for : $(PARSER)"
	python $(PARSER)

	@echo "Running unit test for : $(GCNO_PARSER)"
	python $(GCNO_PARSER)

	@echo "Running unit test for : $(GCDA_PARSER)"
	python $(GCDA_PARSER)

	@echo "Running unit test for : $(ARC_SUMMARY)"
	python $(ARC_SUMMARY)

	@echo "Running unit test for : $(FUNCTION_SUMMARY)"
	python $(FUNCTION_SUMMARY)

	@echo "Running unit test for : $(COVERAGE_REPORT)"
	python $(COVERAGE_REPORT)
