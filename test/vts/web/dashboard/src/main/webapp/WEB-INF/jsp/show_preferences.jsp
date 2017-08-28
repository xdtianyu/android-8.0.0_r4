<%--
  ~ Copyright (c) 2016 Google Inc. All Rights Reserved.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License"); you
  ~ may not use this file except in compliance with the License. You may
  ~ obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
  ~ implied. See the License for the specific language governing
  ~ permissions and limitations under the License.
  --%>
<%@ page contentType='text/html;charset=UTF-8' language='java' %>
<%@ taglib prefix='fn' uri='http://java.sun.com/jsp/jstl/functions' %>
<%@ taglib prefix='c' uri='http://java.sun.com/jsp/jstl/core'%>

<html>
  <%@ include file="header.jsp" %>
  <link rel='stylesheet' href='/css/show_preferences.css'>
  <script type='text/javascript' src='https://ajax.googleapis.com/ajax/libs/jqueryui/1.12.0/jquery-ui.min.js'></script>
  <body>
    <script>
        var subscriptions = ${subscriptionsJson};
        var subscriptionMap = ${subscriptionMapJson};
        var allTests = ${allTestsJson};
        var testSet = new Set(allTests);
        var addedSet = new Set();
        var removedKeySet = new Set();

        var addFunction = function() {
            var test = $('#input-box').val();
            if (testSet.has(test) && !subscriptionMap[test] && !addedSet.has(test)) {
                var icon = $('<i></i>').addClass('material-icons')
                                       .html('clear');
                var clear = $('<a></a>').addClass('btn-flat clear-button')
                                        .append(icon)
                                        .click(clearFunction);
                var span = $('<span></span>').addClass('entry valign')
                                             .html(test);
                var div = $('<div></div>').addClass('col s12 card option valign-wrapper')
                                          .append(span).append(clear)
                                          .prependTo('#options')
                                          .hide()
                                          .slideDown(150);
                if (!subscriptionMap[test]) {
                    addedSet.add(test);
                } else {
                    removedKeySet.delete(subscriptionMap[test].key);
                }
                $('#input-box').val('').focusout();
                if (!addedSet.size && !removedKeySet.size) {
                    $('#save-button-wrapper').slideUp(50);
                } else {
                    $('#save-button-wrapper').slideDown(50);
                }
            }
        }

        var clearFunction = function() {
            var div = $(this).parent();
            div.slideUp(150, function() {
                div.remove();
            });
            var test = div.find('span').text();
            if (subscriptionMap[test]) {
                removedKeySet.add(subscriptionMap[test].key);
            } else {
                addedSet.delete(test);
            }
            if (!addedSet.size && !removedKeySet.size) {
                $('#save-button-wrapper').slideUp(50);
            } else {
                $('#save-button-wrapper').slideDown(50);
            }
        }

        var submitForm = function() {
            var added = Array.from(addedSet).join(',');
            var removed = Array.from(removedKeySet).join(',');
            $('#prefs-form>input[name="addedTests"]').val(added);
            $('#prefs-form>input[name="removedKeys"]').val(removed);
            $('#prefs-form').submit();
        }

        $.widget('custom.sizedAutocomplete', $.ui.autocomplete, {
            _resizeMenu: function() {
                this.menu.element.outerWidth($('#input-box').width());
            }
        });

        $(function() {
            $('#input-box').sizedAutocomplete({
                source: allTests,
                classes: {
                    'ui-autocomplete': 'card'
                }
            });

            $('#input-box').keyup(function(event) {
                if (event.keyCode == 13) {  // return button
                    $('#add-button').click();
                }
            });

            $('.clear-button').click(clearFunction);
            $('#add-button').click(addFunction);
            $('#save-button').click(submitForm);
            $('#save-button-wrapper').hide();
        });
    </script>
    <div class='container'>
      <div class='row'>
        <h3 class='col s12 header'>Favorites</h3>
        <p class='col s12 caption'>Add or remove tests from favorites to customize
            the dashboard. Tests in your favorites will send you email notifications
            to let you know when test cases change status.
        </p>
      </div>
      <div class='row'>
        <div class='input-field col s8'>
          <input type='text' id='input-box'></input>
          <label for='input-box'>Search for tests to add to favorites</label>
        </div>
        <div id='add-button-wrapper' class='col s1 valign-wrapper'>
          <a id='add-button' class='btn-floating btn waves-effect waves-light red valign'><i class='material-icons'>add</i></a>
        </div>
      </div>
      <div class='row' id='options'>
        <c:forEach items='${subscriptions}' var='subscription' varStatus='loop'>
          <div class='col s12 card option valign-wrapper'>
            <span class='entry valign' index=${loop.index} key=${subscription.key}>${subscription.testName}</span>
            <a class='btn-flat clear-button'>
                <i class='material-icons'>clear</i>
            </a>
          </div>
        </c:forEach>
      </div>
    </div>
    <form id='prefs-form' style='visibility:hidden' action='/show_preferences' method='post'>
        <input name='addedTests' type='text'>
        <input name='removedKeys' type='text'>
    </form>
    <div id='save-button-wrapper' class='fixed-action-btn'>
      <a id='save-button' class='btn-floating btn-large red waves-effect'>
        <i class='large material-icons'>done</i>
      </a>
    </div>
    <%@ include file="footer.jsp" %>
  </body>
</html>
