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
 * $Id: TestEditingDomain.java,v 1.4 2007/11/14 18:14:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests.fixtures;

import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.TransactionalEditingDomainImpl;

/**
 * Editing domain implementation used to test the registry.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TestEditingDomain extends TransactionalEditingDomainImpl {
	public static int instanceCount = 0;
	
	public TestEditingDomain(AdapterFactory adapterFactory, ResourceSet resourceSet) {
		super(adapterFactory, resourceSet);
		instanceCount++;
	}

	public TestEditingDomain(AdapterFactory adapterFactory) {
		super(adapterFactory);
		instanceCount++;
	}

	public static class FactoryImpl extends TransactionalEditingDomainImpl.FactoryImpl {

		@Override
		public TransactionalEditingDomain createEditingDomain() {
			TransactionalEditingDomain result = new TestEditingDomain(
					new ComposedAdapterFactory(
						ComposedAdapterFactory.Descriptor.Registry.INSTANCE));
			
			mapResourceSet(result);
			
			return result;
		}

		@Override
		public TransactionalEditingDomain createEditingDomain(ResourceSet rset) {
			// not used by the extension point
			return null;
		}

		@Override
		public TransactionalEditingDomain getEditingDomain(ResourceSet rset) {
			// not used by the extension point
			return null;
		}
		
	}
}
