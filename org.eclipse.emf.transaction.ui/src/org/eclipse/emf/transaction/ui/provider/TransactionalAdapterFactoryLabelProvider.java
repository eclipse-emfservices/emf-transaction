/**
 * <copyright> 
 *
 * Copyright (c) 2005 IBM Corporation and others.
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
 * $Id: TransactionalAdapterFactoryLabelProvider.java,v 1.1 2006/01/30 19:47:45 cdamus Exp $
 */
package org.eclipse.emf.transaction.ui.provider;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.transaction.RunnableWithResult;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.ui.internal.EMFTransactionUIPlugin;
import org.eclipse.emf.transaction.ui.internal.EMFTransactionUIStatusCodes;
import org.eclipse.emf.transaction.ui.internal.Tracing;
import org.eclipse.emf.transaction.ui.internal.l10n.Messages;
import org.eclipse.swt.graphics.Image;


/**
 * Automatically wraps any potential access to model objects in read transactions.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class TransactionalAdapterFactoryLabelProvider
	extends org.eclipse.emf.edit.ui.provider.AdapterFactoryLabelProvider {

	private final TransactionalEditingDomain domain;
	
	/**
	 * Initializes me with the editing domain in which I create read
	 * transactions and that adapter factory that provides content providers.
	 * 
	 * @param domain my editing domain
	 * @param adapterFactory the adapter factory
	 */
	public TransactionalAdapterFactoryLabelProvider(TransactionalEditingDomain domain, AdapterFactory adapterFactory) {
		super(adapterFactory);
		
		this.domain = domain;
	}

	/**
	 * Runs the specified runnable in the editing domain, with interrupt
	 * handling.
	 * 
	 * @param run the runnable to run
	 * 
	 * @return its result, or <code>null</code> on interrupt
	 */
	protected Object run(RunnableWithResult run) {
		try {
			return domain.runExclusive(run);
		} catch (InterruptedException e) {
			Tracing.catching(TransactionalAdapterFactoryLabelProvider.class, "run", e); //$NON-NLS-1$
			
			// propagate interrupt status because we are not throwing
			Thread.currentThread().interrupt();
			
			EMFTransactionUIPlugin.INSTANCE.log(new Status(
				IStatus.ERROR,
				EMFTransactionUIPlugin.getPluginId(),
				EMFTransactionUIStatusCodes.LABEL_PROVIDER_INTERRUPTED,
				Messages.labelInterrupt,
				e));
			
			return null;
		}
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	public Image getColumnImage(final Object object, final int columnIndex) {
		return (Image) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getColumnImage(object, columnIndex));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	public String getColumnText(final Object object, final int columnIndex) {
		return (String) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getColumnText(object, columnIndex));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	protected Image getDefaultImage(final Object object) {
		return (Image) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getDefaultImage(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	public Image getImage(final Object object) {
		return (Image) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getImage(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	protected Image getImageFromObject(final Object object) {
		return (Image) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getImageFromObject(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	public String getText(final Object object) {
		return (String) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getText(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	public boolean isLabelProperty(final Object object, final String id) {
		Boolean result = (Boolean) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.isLabelProperty(object, id)
						? Boolean.TRUE : Boolean.FALSE);
			}});
		
		return (result == null) ? false : result.booleanValue();
	}
}
