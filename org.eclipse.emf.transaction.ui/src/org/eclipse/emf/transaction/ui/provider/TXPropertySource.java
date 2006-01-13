/**
 * <copyright> 
 *
 * Copyright (c) 2005, 2006 IBM Corporation and others.
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
 * $Id: TXPropertySource.java,v 1.2 2006/01/13 21:50:17 cdamus Exp $
 */
package org.eclipse.emf.transaction.ui.provider;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.RunnableWithResult;
import org.eclipse.emf.transaction.TXEditingDomain;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.impl.InternalTXEditingDomain;
import org.eclipse.emf.transaction.ui.internal.EMFTransactionUIPlugin;
import org.eclipse.emf.transaction.ui.internal.EMFTransactionUIStatusCodes;
import org.eclipse.emf.transaction.ui.internal.Tracing;
import org.eclipse.emf.transaction.ui.internal.l10n.Messages;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;
import org.eclipse.ui.views.properties.IPropertySource2;


/**
 * A property source wrapper that calls its delegate in transactions of the
 * appropriate kind.  Note that transactions are not actually required for
 * methods that do not access model objects.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class TXPropertySource
	implements IPropertySource2 {

	private final TXEditingDomain domain;
	
	private final IPropertySource propertySource;
	private final IPropertySource2 propertySource2;
	
	/**
	 * Initializes me with the editing domain in which I create transactions,
	 * and the property source that I delegate to.
	 * 
	 * @param domain my editing domain
	 * @param propertySource my delegate
	 */
	public TXPropertySource(TXEditingDomain domain, IPropertySource propertySource) {
		this.domain = domain;
		
		this.propertySource = propertySource;
		this.propertySource2 = (propertySource instanceof IPropertySource2)
			? (IPropertySource2) propertySource
			: null;
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
			Tracing.catching(TXPropertySource.class, "run", e); //$NON-NLS-1$
			
			// propagate interrupt status because we are not throwing
			Thread.currentThread().interrupt();
			
			EMFTransactionUIPlugin.INSTANCE.log(new Status(
				IStatus.ERROR,
				EMFTransactionUIPlugin.getPluginId(),
				EMFTransactionUIStatusCodes.PROPERTY_SHEET_INTERRUPTED,
				Messages.propertyInterrupt,
				e));
			
			return null;
		}
	}

	/**
	 * Delegates the my wrapped property source in the appropriate transaction.
	 */
	public boolean isPropertyResettable(Object id) {
		return (propertySource2 == null)? false
				: propertySource2.isPropertyResettable(id);
	}

	/**
	 * Delegates the my wrapped property source in the appropriate transaction.
	 */
	public boolean isPropertySet(final Object id) {
		Boolean result = (Boolean) run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(propertySource.isPropertySet(id)
						? Boolean.TRUE : Boolean.FALSE);
			}});
		
		return (result == null) ? false : result.booleanValue();
	}

	/**
	 * Delegates the my wrapped property source in the appropriate transaction.
	 */
	public Object getEditableValue() {
		return run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(propertySource.getEditableValue());
			}});
	}

	/**
	 * Delegates the my wrapped property source in the appropriate transaction.
	 */
	public IPropertyDescriptor[] getPropertyDescriptors() {
		return propertySource.getPropertyDescriptors();
	}

	/**
	 * Delegates the my wrapped property source in the appropriate transaction.
	 */
	public Object getPropertyValue(final Object id) {
		return run(new RunnableWithResult.Impl() {
			public void run() {
				setResult(propertySource.getPropertyValue(id));
			}});
	}

	/**
	 * Delegates the my wrapped property source in the appropriate transaction.
	 */
	public void resetPropertyValue(final Object id) {
		if (propertySource2 != null) {
			// are we in a read-only context?  if so, balk because we cannot upgrade
			//    read transaction to write when executing a command
			Transaction tx = ((InternalTXEditingDomain) domain).getActiveTransaction();
			if ((tx == null) || !tx.isReadOnly()) {
				propertySource2.resetPropertyValue(id);
			}
		}
	}

	/**
	 * Delegates directly to the wrapper property source, because it will use
	 * a command to perform the modification (which implicitly creates a
	 * read/write transaction).
	 */
	public void setPropertyValue(final Object id, final Object value) {
		// are we in a read-only context?  if so, balk because we cannot upgrade
		//    read transaction to write when executing a command
		Transaction tx = ((InternalTXEditingDomain) domain).getActiveTransaction();
		if ((tx == null) || !tx.isReadOnly()) {
			propertySource.setPropertyValue(id, value);
		}
	}
}
