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
 * $Id: TransactionalAdapterFactoryLabelProvider.java,v 1.3 2007/11/14 18:14:06 cdamus Exp $
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
import org.eclipse.emf.transaction.util.TransactionUtil;
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
	 * @param <T> the result type of the runnable
	 * 
	 * @param run the runnable to run
	 * 
	 * @return its result, or <code>null</code> on interrupt
	 */
	protected <T> T run(RunnableWithResult<? extends T> run) {
		try {
			return TransactionUtil.runExclusive(domain, run);
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
	@Override
	public Image getColumnImage(final Object object, final int columnIndex) {
		return run(new RunnableWithResult.Impl<Image>() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getColumnImage(object, columnIndex));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	@Override
	public String getColumnText(final Object object, final int columnIndex) {
		return run(new RunnableWithResult.Impl<String>() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getColumnText(object, columnIndex));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	@Override
	protected Image getDefaultImage(final Object object) {
		return run(new RunnableWithResult.Impl<Image>() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getDefaultImage(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	@Override
	public Image getImage(final Object object) {
		return run(new RunnableWithResult.Impl<Image>() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getImage(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	@Override
	protected Image getImageFromObject(final Object object) {
		return run(new RunnableWithResult.Impl<Image>() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getImageFromObject(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	@Override
	public String getText(final Object object) {
		return run(new RunnableWithResult.Impl<String>() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.getText(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	@Override
	public boolean isLabelProperty(final Object object, final String id) {
		return run(new RunnableWithResult.Impl<Boolean>() {
			public void run() {
				setResult(TransactionalAdapterFactoryLabelProvider.super.isLabelProperty(object, id));
			}});
	}
}
