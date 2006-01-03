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
 * $Id: TXAdapterFactoryContentProvider.java,v 1.1 2006/01/03 20:44:14 cdamus Exp $
 */
package org.eclipse.emf.transaction.ui.provider;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.edit.provider.IItemPropertySource;
import org.eclipse.emf.transaction.RunnableWithResult;
import org.eclipse.emf.transaction.TXEditingDomain;
import org.eclipse.emf.transaction.ui.internal.EMFTransactionUIPlugin;
import org.eclipse.emf.transaction.ui.internal.EMFTransactionUIStatusCodes;
import org.eclipse.emf.transaction.ui.internal.Tracing;
import org.eclipse.emf.transaction.ui.internal.l10n.Messages;
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
public class TXAdapterFactoryContentProvider
	extends org.eclipse.emf.edit.ui.provider.AdapterFactoryContentProvider {

	private final TXEditingDomain domain;
	
	/**
	 * Initializes me with the editing domain in which I create read
	 * transactions and that adapter factory that provides content providers.
	 * 
	 * @param domain my editing domain
	 * @param adapterFactory the adapter factory
	 */
	public TXAdapterFactoryContentProvider(TXEditingDomain domain, AdapterFactory adapterFactory) {
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
			Tracing.catching(TXAdapterFactoryContentProvider.class, "run", e); //$NON-NLS-1$
			
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
	protected IPropertySource createPropertySource(final Object object, final IItemPropertySource itemPropertySource) {
		return wrap((IPropertySource) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TXAdapterFactoryContentProvider.super.createPropertySource(object, itemPropertySource));
			}}));
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	public Object[] getChildren(final Object object) {
		return (Object[]) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TXAdapterFactoryContentProvider.super.getChildren(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	public Object[] getElements(final Object object) {
		return (Object[]) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TXAdapterFactoryContentProvider.super.getElements(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	public Object getParent(final Object object) {
		return run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TXAdapterFactoryContentProvider.super.getParent(object));
			}});
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 * The returned property source also uses transactions to access properties.
	 */
	public IPropertySource getPropertySource(final Object object) {
		return wrap((IPropertySource) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TXAdapterFactoryContentProvider.super.getPropertySource(object));
			}}));
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	public boolean hasChildren(final Object object) {
		Boolean result = (Boolean) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(TXAdapterFactoryContentProvider.super.hasChildren(object)
						? Boolean.TRUE : Boolean.FALSE);
			}});
		
		return (result == null) ? false : result.booleanValue();
	}

	/**
	 * Extends the inherited implementation by running in a read-only transaction.
	 */
	public void inputChanged(final Viewer vwr, final Object oldInput, final Object newInput) { 
		run(new RunnableWithResult.Impl() {
			public void run() {
				TXAdapterFactoryContentProvider.super.inputChanged(vwr, oldInput, newInput);
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
		return (propertySource == null) ? null : new TXPropertySource(domain, propertySource);
	}
}
