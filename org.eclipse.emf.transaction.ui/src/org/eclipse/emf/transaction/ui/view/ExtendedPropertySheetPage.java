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
 * $Id: ExtendedPropertySheetPage.java,v 1.3 2007/06/07 14:26:08 cdamus Exp $
 */
package org.eclipse.emf.transaction.ui.view;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.ui.internal.EMFTransactionUIPlugin;
import org.eclipse.emf.transaction.ui.internal.EMFTransactionUIStatusCodes;
import org.eclipse.emf.transaction.ui.internal.Tracing;
import org.eclipse.emf.transaction.ui.internal.l10n.Messages;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchPart;


/**
 * An extension of the extended property sheet page that performs refreshes
 * in read transactions.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class ExtendedPropertySheetPage
	extends org.eclipse.emf.edit.ui.view.ExtendedPropertySheetPage {

	/**
	 * Initializes me with the editing domain in which I create transactions.
	 * 
	 * @param editingDomain my editing domain
	 */
	public ExtendedPropertySheetPage(AdapterFactoryEditingDomain editingDomain) {
		super(editingDomain);
	}

	/**
	 * Obtains my editing domain.
	 * 
	 * @return my editing domain
	 */
	protected TransactionalEditingDomain getTransactionalEditingDomain() {
		return (TransactionalEditingDomain) editingDomain;
	}
	
	/**
	 * Delegates to the superclass implementation within a read transaction,
	 * if an editing domain is available.
	 */
	public void refresh() {
		TransactionalEditingDomain domain = getTransactionalEditingDomain();
		
		if (domain == null) {
			super.refresh();
		} else {
			try {
				domain.runExclusive(new Runnable() {
					public void run() {
						ExtendedPropertySheetPage.super.refresh();
					}});
			} catch (InterruptedException e) {
				Tracing.catching(ExtendedPropertySheetPage.class, "refresh", e); //$NON-NLS-1$
				
				// propagate interrupt status because we are not throwing
				Thread.currentThread().interrupt();
				
				EMFTransactionUIPlugin.INSTANCE.log(new Status(
					IStatus.ERROR,
					EMFTransactionUIPlugin.getPluginId(),
					EMFTransactionUIStatusCodes.PROPERTY_SHEET_INTERRUPTED,
					Messages.propertyInterrupt,
					e));
			}
		}
	}
	
	/**
	 * Delegates to the superclass implementation within a read transaction,
	 * if an editing domain is available.
	 */
	public void selectionChanged(final IWorkbenchPart part, final ISelection selection) {
		TransactionalEditingDomain domain = getTransactionalEditingDomain();
		
		if (domain == null) {
			super.selectionChanged(part, selection);
		} else {
			try {
				domain.runExclusive(new Runnable() {
					public void run() {
						ExtendedPropertySheetPage.super.selectionChanged(part, selection);
					}});
			} catch (InterruptedException e) {
				Tracing.catching(ExtendedPropertySheetPage.class, "selectionChanged", e); //$NON-NLS-1$
				
				// propagate interrupt status because we are not throwing
				Thread.currentThread().interrupt();
				
				EMFTransactionUIPlugin.INSTANCE.log(new Status(
					IStatus.ERROR,
					EMFTransactionUIPlugin.getPluginId(),
					EMFTransactionUIStatusCodes.PROPERTY_SHEET_INTERRUPTED,
					Messages.propertyInterrupt,
					e));
			}
		}
	}
}
