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
 * $Id: TestEditingDomain.java,v 1.1 2006/01/03 20:51:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests.fixtures;

import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.transaction.TXEditingDomain;
import org.eclipse.emf.transaction.impl.TXEditingDomainImpl;

/**
 * Editing domain implementation used to test the registry.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TestEditingDomain extends TXEditingDomainImpl {
	public static int instanceCount = 0;
	
	public TestEditingDomain(AdapterFactory adapterFactory, ResourceSet resourceSet) {
		super(adapterFactory, resourceSet);
		instanceCount++;
	}

	public TestEditingDomain(AdapterFactory adapterFactory) {
		super(adapterFactory);
		instanceCount++;
	}

	public static class FactoryImpl extends TXEditingDomainImpl.FactoryImpl {

		public TXEditingDomain createEditingDomain() {
			TXEditingDomain result = new TestEditingDomain(
					new ComposedAdapterFactory(
						ComposedAdapterFactory.Descriptor.Registry.INSTANCE));
			
			mapResourceSet(result);
			
			return result;
		}

		public TXEditingDomain createEditingDomain(ResourceSet rset) {
			// not used by the extension point
			return null;
		}

		public TXEditingDomain getEditingDomain(ResourceSet rset) {
			// not used by the extension point
			return null;
		}
		
	}
}
