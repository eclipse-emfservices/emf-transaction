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
package org.eclipse.emf.transaction.ui.provider;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.edit.provider.IItemPropertySource;
import org.eclipse.emf.transaction.RunnableWithResult;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.ui.internal.EMFTransactionUIPlugin;
import org.eclipse.emf.transaction.ui.internal.EMFTransactionUIStatusCodes;
import org.eclipse.emf.transaction.ui.internal.Tracing;
import org.eclipse.emf.transaction.ui.internal.l10n.Messages;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.ui.views.properties.IPropertySource;


/**
 * Automatically wraps any potential access to model objects in read transactions.
 * Note that this is not necessary in the case of the
 * {@link org.eclipse.emf.edit.ui.provider.AdapterFactoryContentProvider#notifyChanged(Notification)}
 * method because this will always be called in a transaction context.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class TransactionalAdapterFactoryContentProvider
	extends org.eclipse.emf.edit.ui.provider.AdapterFactoryContentProvider {

	private final TransactionalEditingDomain domain;
	
	/**
	 * Initializes me with the editing domain in which I create read
	 * transactions and that adapter factory that provides content providers.
	 * 
	 * @param domain my editing domain
	 * @param adapterFactory the adapter factory
	 */
	public TransactionalAdapterFactoryContentProvider(TransactionalEditingDomain domain, AdapterFactory adapterFactory) {
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
			Tracing.catching(TransactionalAdapterFactoryContentProvider.class, "run", e); //$NON-NLS-1$
			
			// propagate interrupt status because we are not throwing
			Thread.currentThread().interrupt();
			
			EMFTransactionUIPlugin.INSTANCE.log(new Status(
				IStatus.ERROR,
				EMFTransactionUIPlugin.getPluginId(),
				EMFTransactionUIStatusCodes.CONTENT_PROVIDER_INTERRUPTED,
				Messages.contentInterrupt,
				e));
			
			return null;
		}
	}
	
	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 * The returned property source also uses transactions to access properties.
	 */
	@Override
	protected IPropertySource createPropertySource(final Object object, final IItemPropertySource itemPropertySource) {
		return wrap(run(new RunnableWithResult.Impl<IPropertySource>() {
			public void run() {
				setResult(TransactionalAdapterFactoryContentProvider.super.createPropertySource(object, itemPropertySource));
			}}));
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	@Override
	public Object[] getChildren(final Object object) {
		return run(new RunnableWithResult.Impl<Object[]>() {
			public void run() {
				setResult(TransactionalAdapterFactoryContentProvider.super.getChildren(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	@Override
	public Object[] getElements(final Object object) {
		return run(new RunnableWithResult.Impl<Object[]>() {
			public void run() {
				setResult(TransactionalAdapterFactoryContentProvider.super.getElements(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	@Override
	public Object getParent(final Object object) {
		return run(new RunnableWithResult.Impl<Object>() {
			public void run() {
				setResult(TransactionalAdapterFactoryContentProvider.super.getParent(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 * The returned property source also uses transactions to access properties.
	 */
	@Override
	public IPropertySource getPropertySource(final Object object) {
		return wrap(run(new RunnableWithResult.Impl<IPropertySource>() {
			public void run() {
				setResult(TransactionalAdapterFactoryContentProvider.super.getPropertySource(object));
			}}));
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	@Override
	public boolean hasChildren(final Object object) {
		return run(new RunnableWithResult.Impl<Boolean>() {
			public void run() {
				setResult(TransactionalAdapterFactoryContentProvider.super.hasChildren(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	@Override
	public void inputChanged(final Viewer vwr, final Object oldInput, final Object newInput) { 
		run(new RunnableWithResult.Impl<Object>() {
			public void run() {
				TransactionalAdapterFactoryContentProvider.super.inputChanged(vwr, oldInput, newInput);
			}});
	}

	/**
	 * Wraps a property source in a transactional property source.
	 * 
	 * @param propertySource the property source to wrap
	 * 
	 * @return a wrapper that delegates to the original property source within
	 *     transactions
	 */
	protected IPropertySource wrap(IPropertySource propertySource) {
		return (propertySource == null) ? null : new TransactionalPropertySource(domain, propertySource);
	}
}
