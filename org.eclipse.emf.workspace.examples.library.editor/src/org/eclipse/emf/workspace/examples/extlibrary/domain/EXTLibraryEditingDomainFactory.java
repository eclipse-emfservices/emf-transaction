/**
 * <copyright>
 *
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: EXTLibraryEditingDomainFactory.java,v 1.4 2007/10/03 20:17:42 cdamus Exp $
 */
package org.eclipse.emf.workspace.examples.extlibrary.domain;

import java.util.Collection;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalCommandStack;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TransactionalEditingDomain.DefaultOptions;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.emf.workspace.WorkspaceEditingDomainFactory;
import org.eclipse.emf.workspace.util.WorkspaceValidateEditSupport;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

/**
 * An editing domain factory registered on the extension point to create our
 * shared editing domain for EXTLibrary model editors.
 *
 * @author Christian W. Damus (cdamus)
 */
public class EXTLibraryEditingDomainFactory implements TransactionalEditingDomain.Factory {

	public TransactionalEditingDomain createEditingDomain() {
		// create an editing domain with a default resource set implementation
		//    and delegating command execution to the default (workbench)
		//    operation history
		TransactionalEditingDomain result = WorkspaceEditingDomainFactory.INSTANCE.createEditingDomain();
		
		// add an exception handler to the editing domain's command stack
		((TransactionalCommandStack) result.getCommandStack()).setExceptionHandler(
				new CommandStackExceptionHandler());
		
		DefaultOptions defaults = (DefaultOptions) TransactionUtil.getAdapter(
            result, DefaultOptions.class);
		if (defaults != null) {
            Map options = new java.util.HashMap();

            options.put(Transaction.OPTION_VALIDATE_EDIT,
                new WorkspaceValidateEditSupport() {
                    protected IStatus doValidateEdit(Transaction transaction,
                            Collection resources, Object context) {
                        if ((context == null) && (Display.getCurrent() != null)) {
                            // get the active shell for the context
                            // when validating on the UI thread
                            IWorkbench wb = PlatformUI.getWorkbench();
                            if (wb.getActiveWorkbenchWindow() != null) {
                                context = wb.getActiveWorkbenchWindow().getShell();
                            }
                        }
                        
                        return super.doValidateEdit(transaction, resources, context);
                    }
                });

            defaults.setDefaultTransactionOptions(options);
        }
		
		return result;
	}

	public TransactionalEditingDomain createEditingDomain(ResourceSet rset) {
		// not used when initializing editing domain from extension point
		return null;
	}

	public TransactionalEditingDomain getEditingDomain(ResourceSet rset) {
		// not used when initializing editing domain from extension point
		return null;
	}

}
