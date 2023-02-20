/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.workspace.examples.extlibrary.domain;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.ExceptionHandler;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.workspace.examples.extlibrary.internal.l10n.Messages;
import org.eclipse.emf.workspace.examples.extlibrary.presentation.EXTLibraryEditorPlugin;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * An exception handler for the shared editing domain's command stack, that
 * shows errors in a pop-up dialog.
 *
 * @author Christian W. Damus (cdamus)
 */
public class CommandStackExceptionHandler implements ExceptionHandler {

	// Documentation copied from the inherited specification
	public void handleException(final Exception e) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			public void run() {
				Shell shell = null;
				IWorkbenchWindow window =
					PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				
				if (window != null) {
					shell = window.getShell();
				}
				
				if (e instanceof RollbackException) {
					RollbackException rbe = (RollbackException) e;
					
					ErrorDialog.openError(
							shell,
							Messages.cmdFailed,
							Messages.cmdRollback,
							rbe.getStatus());
				} else {
					ErrorDialog.openError(
							shell,
							Messages.cmdFailed,
							Messages.cmdException,
							new Status(
									IStatus.ERROR,
									EXTLibraryEditorPlugin.getPlugin().getSymbolicName(),
									1,
									e.getLocalizedMessage(),
									e));
				}
			}});
	}

}
