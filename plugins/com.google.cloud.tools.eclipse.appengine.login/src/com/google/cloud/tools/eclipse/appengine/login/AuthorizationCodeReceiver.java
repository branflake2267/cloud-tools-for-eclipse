/*******************************************************************************
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/

package com.google.cloud.tools.eclipse.appengine.login;

import com.google.common.base.Preconditions;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Runs a Jetty web server instance that opens up an HTTP listening port to receive OAuth2
 * authorization code.
 */
public class AuthorizationCodeReceiver {

  private static final Logger logger = Logger.getLogger(AuthorizationCodeReceiver.class.getName());

  // TODO: create landing pages.
  private static final String SUCCESS_LANDING_PAGE = "https://cloud.google.com/tools/intellij/auth_success";
  private static final String FAILURE_LANDING_PAGE = "https://cloud.google.com/tools/intellij/auth_failure";

  private static final String LOCAL_HOST = "127.0.0.1";
  private static final String CODE_RECEPTION_POINT_PATH = "/code-reception-point";

  private Server server;

  private volatile boolean resultConsumed;

  // Reads on these shared objects can happen only after server shutdown,
  // so synchronization is not really necessary.
  private String error;
  private String authorizationCode;

  /**
   * Starts a local server listening through an ephemeral port.
   *
   * @return "http://127.0.0.1:<listening port>/code-reception-point", where this server listens
   *     and expects to get an authorization code returning from a OAuth login flow
   * @throws IOException wraps any {@link Exception} from Jetty when it failed to start
   */
  public String runLocalServer() throws IOException {
    Preconditions.checkState(server == null, "Don't reuse the receiver.");

    server = new Server(0);
    server.setHandler(new RequestHandler());
    ServerConnector connector = (ServerConnector) server.getConnectors()[0];
    connector.setHost(LOCAL_HOST);
    try {
      server.setStopAtShutdown(true);
      server.start();
      logger.log(Level.INFO, "Started a local web server waiting for OAuth2 authorization code.");
      return "http://127.0.0.1:" + connector.getLocalPort() + CODE_RECEPTION_POINT_PATH;
    } catch (Exception ex) {
      throw new IOException(ex);
    }
  }

  /**
   * Waits for a login result to arrive for the given timeout or until interrupted. Intended to
   * be called in a polling loop until it returns true.
   *
   * @param timeout in millisecond. Cannot be 0
   * @return true if the web server consumed the login result
   */
  public boolean waitForCode(long timeout) {
    Preconditions.checkArgument(timeout != 0);
    Preconditions.checkState(server != null, "Call runLocalServer() first.");

    synchronized (server) {
      try {
        server.wait(timeout);
      } catch (InterruptedException e) {}
    }

    return resultConsumed;
  }

  public void shutdown() {
    Preconditions.checkState(server != null, "Server was never created.");

    try {
      server.stop();
      server.join();
    } catch (Exception ex) {
      logger.log(Level.WARNING, "Failed to stop the local web server for login.", ex);
    }
  }

  public String getAuthorizationCode() {
    Preconditions.checkState(server != null, "Server was never created.");
    Preconditions.checkState(resultConsumed, "Call this when waitForCode() returns true.");

    return authorizationCode;
  }

  /**
   * Jetty handler that takes the authorization code passed over from the OAuth provider
   * and stashes it where {@link #waitForCode} will find it.
   */
  private class RequestHandler extends AbstractHandler {

    @Override
    public void handle(String target, Request baseRequest,
        HttpServletRequest request, HttpServletResponse response) throws IOException {
      if (!CODE_RECEPTION_POINT_PATH.equals(target)) {
        return;
      }

      resultConsumed = true;
      baseRequest.setHandled(true);
      error = request.getParameter("error");
      authorizationCode = request.getParameter("code");

      response.sendRedirect(error == null ? SUCCESS_LANDING_PAGE : FAILURE_LANDING_PAGE);
      response.flushBuffer();
    }
  }
}
