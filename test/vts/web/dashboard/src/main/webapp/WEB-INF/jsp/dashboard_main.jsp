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
  <link rel='stylesheet' href='/css/dashboard_main.css'>
  <%@ include file="header.jsp" %>
  <body>
    <div class='container'>
      <div class='row' id='options'>
        <c:choose>
          <c:when test="${not empty error}">
            <div id="error-container" class="row card">
              <div class="col s12 center-align">
                <h5>${error}</h5>
              </div>
            </div>
          </c:when>
          <c:otherwise>
            <div class='col s12'>
              <h4 id='section-header'>${headerLabel}</h4>
            </div>
            <c:forEach items='${testNames}' var='test'>
              <a href='/show_table?testName=${test.name}'>
                <div class='col s12 card hoverable option valign-wrapper waves-effect'>
                  <span class='entry valign'>${test.name}
                    <c:if test='${test.failCount > 0}'>
                      <span class='indicator red center'>
                        ${test.failCount}
                      </span>
                    </c:if>
                  </span>
                </div>
              </a>
            </c:forEach>
          </c:otherwise>
        </c:choose>
      </div>
      <div class='row center-align'>
        <a href='${buttonLink}' id='show-button' class='btn waves-effect red'>${buttonLabel}
          <i id='show-button-arrow' class='material-icons right'>${buttonIcon}</i>
        </a>
      </div>
    </div>
    <c:if test='${not showAll}'>
      <div id='edit-button-wrapper' class='fixed-action-btn'>
        <a href='/show_preferences' id='edit-button' class='btn-floating btn-large red waves-effect'>
          <i class='large material-icons'>mode_edit</i>
        </a>
      </div>
    </c:if>
    <%@ include file="footer.jsp" %>
  </body>
</html>
