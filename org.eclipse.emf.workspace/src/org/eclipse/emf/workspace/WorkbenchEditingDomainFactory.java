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
 * $Id: WorkbenchEditingDomainFactory.java,v 1.1 2006/01/30 16:18:18 cdamus Exp $
 */
package org.eclipse.emf.workspace;

import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.transaction.TXEditingDomain;
import org.eclipse.emf.transaction.impl.TXEditingDomainImpl;
import org.eclipse.emf.workspace.impl.WorkbenchCommandStackImpl;

/**
 * Factory for creating transactional editing domains that delegate
 * command execution, undo, and redo to an {@link IOperationHistory}.
 *
 * @author Christian W. Damus (cdamus)
 */
public class WorkbenchEditingDomainFactory extends TXEditingDomainImpl.FactoryImpl {

	/**
	 * The single shared instance of the workbench editing domain factory.
	 */
	public static final WorkbenchEditingDomainFactory INSTANCE =
		new WorkbenchEditingDomainFactory();
	
	/**
	 * Initializes me.
	 */
	public WorkbenchEditingDomainFactory() {
		super();
	}
	
	/**
	 * Creates a new editing domain using a default resource set implementation
	 * and the Workbench's shared operation history.
	 * 
	 * @return the new editing domain
	 */
	public TXEditingDomain createEditingDomain() {
		return createEditingDomain(OperationHistoryFactory.getOperationHistory());
	}

	/**
	 * Creates a new editing domain using the given resource set
	 * and the Workbench's shared operation history.
	 * 
	 * @param the resource set on which to create the editing domain
	 * 
	 * @return the new editing domain
	 */
	public TXEditingDomain createEditingDomain(ResourceSet rset) {
		return createEditingDomain(
				rset,
				OperationHistoryFactory.getOperationHistory());
	}

	/**
	 * Creates a new editing domain on a default resource set implementation and
	 * the specified operation history.
	 * 
	 * @param history the operation history to which I delegate the command stack
	 * 
	 * @return the new editing domain
	 */
	public TXEditingDomain createEditingDomain(IOperationHistory history) {
		WorkbenchCommandStackImpl stack = new WorkbenchCommandStackImpl(history);
		
		TXEditingDomain result = new TXEditingDomainImpl(
			new ComposedAdapterFactory(
				ComposedAdapterFactory.Descriptor.Registry.INSTANCE),
			stack);
		
		mapResourceSet(result);
		
		return result;
	}

	/**
	 * Creates a new editing domain on the given resource set and
	 * the specified operation history.
	 * 
	 * @param rset the resource set to use
	 * @param history the operation history to which I delegate the command stack
	 * 
	 * @return the new editing domain
	 */
	public TXEditingDomain createEditingDomain(ResourceSet rset, IOperationHistory history) {
		WorkbenchCommandStackImpl stack = new WorkbenchCommandStackImpl(history);
		
		TXEditingDomain result = new TXEditingDomainImpl(
			new ComposedAdapterFactory(
				ComposedAdapterFactory.Descriptor.Registry.INSTANCE),
			stack,
			rset);
		
		mapResourceSet(result);
		
		return result;
	}
}
