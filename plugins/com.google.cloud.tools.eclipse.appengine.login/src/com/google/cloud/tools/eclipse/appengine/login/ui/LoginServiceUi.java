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

package com.google.cloud.tools.eclipse.appengine.login.ui;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.cloud.tools.eclipse.appengine.login.AuthorizationCodeReceiver;
import com.google.cloud.tools.eclipse.appengine.login.GoogleLoginService;
import com.google.cloud.tools.eclipse.appengine.login.Messages;
import com.google.cloud.tools.ide.login.UiFacade;
import com.google.cloud.tools.ide.login.VerificationCodeHolder;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.services.IServiceLocator;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class LoginServiceUi implements UiFacade {

  private IServiceLocator serviceLocator;
  private IShellProvider shellProvider;
  private Display display;

  public LoginServiceUi(IServiceLocator serviceLocator, IShellProvider shellProvider,
      Display display) {
    this.serviceLocator = serviceLocator;
    this.shellProvider = shellProvider;
    this.display = display;
  }

  public void showErrorDialogHelper(String title, String message) {
    MessageDialog.openError(shellProvider.getShell(), title, message);
  }

  @Override
  public boolean askYesOrNo(String title, String message) {
    throw new RuntimeException("Not allowed to ensure non-UI threads don't prompt."); //$NON-NLS-1$
  }

  @Override
  public void showErrorDialog(String title, String message) {
    // Ignore "title" and "message", as they are non-localized hard-coded strings in the library.
    showErrorDialogHelper(Messages.LOGIN_ERROR_DIALOG_TITLE, Messages.LOGIN_ERROR_DIALOG_MESSAGE);
  }

  @Override
  public void notifyStatusIndicator() {
    // Update and refresh the menu, toolbar button, and tooltip.
    display.asyncExec(new Runnable() {
      @Override
      public void run() {
        serviceLocator.getService(ICommandService.class).refreshElements(
            "com.google.cloud.tools.eclipse.appengine.login.commands.loginCommand", //$NON-NLS-1$
            null);
      }
    });
  }

  @Override
  public VerificationCodeHolder obtainVerificationCodeFromExternalUserInteraction(String title) {
    AuthorizationCodeReceiver codeReceiver = new AuthorizationCodeReceiver();

    String redirectUrl;
    try {
      redirectUrl = codeReceiver.runLocalServer();
    } catch (IOException ex) {
      showErrorDialogHelper(Messages.LOGIN_ERROR_DIALOG_TITLE,
          Messages.LOGIN_ERROR_LOCAL_SERVER_INIT
              + ex.getLocalizedMessage());
      return null;
    }

    try {
      if (!Program.launch(GoogleLoginService.getGoogleLoginUrl(redirectUrl))) {
        showErrorDialogHelper(
            Messages.LOGIN_ERROR_DIALOG_TITLE, Messages.LOGIN_ERROR_CANNOT_OPEN_BROWSER);
        return null;
      }

      showProgressDialogAndWaitForCode(codeReceiver);
      return new VerificationCodeHolder(codeReceiver.getAuthorizationCode(), redirectUrl);

    } catch (InvocationTargetException ex) {
      showErrorDialogHelper(Messages.LOGIN_ERROR_DIALOG_TITLE,
          Messages.LOGIN_ERROR_LOCAL_SERVER_RUN
              + ex.getLocalizedMessage());
      return null;
    } catch (InterruptedException ex) {
      return null;  // User pushed the cancel button.
    }
    finally {
      codeReceiver.shutdown();
    }
  }

  private void showProgressDialogAndWaitForCode(final AuthorizationCodeReceiver codeReceiver)
      throws InvocationTargetException, InterruptedException {
    new ProgressMonitorDialog(shellProvider.getShell()).run(true /* fork */, true /* cancelable */,
        new IRunnableWithProgress() {
          @Override
          public void run(IProgressMonitor monitor)
              throws InvocationTargetException, InterruptedException {
            while (!codeReceiver.waitForCode(500 /* timeout: 0.5 second */)) {
              if (monitor.isCanceled()) {
                throw new InterruptedException();
              }
            }
          }
        });
  }

  @Override
  public String obtainVerificationCodeFromUserInteraction(
      String title, GoogleAuthorizationCodeRequestUrl authCodeRequestUrl) {
    throw new RuntimeException("Not to be called."); //$NON-NLS-1$
  }
}
