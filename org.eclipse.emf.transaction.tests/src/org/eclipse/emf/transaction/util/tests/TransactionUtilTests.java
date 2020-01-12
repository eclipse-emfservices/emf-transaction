/**
 * Copyright (c) 2006, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.util.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.tests.AbstractTest;
import org.eclipse.emf.transaction.util.TransactionUtil;

/**
 * Tests the {@link TransactionUtil} class.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TransactionUtilTests extends AbstractTest {
	public TransactionUtilTests(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(TransactionUtilTests.class, "TransactionUtil Tests"); //$NON-NLS-1$
	}
	
	/**
	 * Tests the <code>getEditingDomain(EObject)</code> method.
	 */
	public void test_getEditingDomain_EObject() {
		assertSame(domain, TransactionUtil.getEditingDomain(root));
		assertNull(TransactionUtil.getEditingDomain(
				EXTLibraryFactory.eINSTANCE.createLibrary()));
	}
	
	/**
	 * Tests the <code>getEditingDomain(Resource)</code> method.
	 */
	public void test_getEditingDomain_Resource() {
		assertSame(domain, TransactionUtil.getEditingDomain(testResource));
		assertNull(TransactionUtil.getEditingDomain(new ResourceImpl()));
	}
	
	/**
	 * Tests the <code>getEditingDomain(ResourceSet)</code> method.
	 */
	public void test_getEditingDomain_ResourceSet() {
		assertSame(domain, TransactionUtil.getEditingDomain(testResource.getResourceSet()));
		assertNull(TransactionUtil.getEditingDomain(new ResourceSetImpl()));
	}
	
	/**
	 * Tests the <code>getEditingDomain(Object)</code> method.
	 */
	public void test_getEditingDomain_Object() {
		assertSame(domain, TransactionUtil.getEditingDomain((Object) root));
		assertSame(domain, TransactionUtil.getEditingDomain((Object) testResource));
		assertSame(domain, TransactionUtil.getEditingDomain((Object) testResource.getResourceSet()));
		
		startReading();
		Transaction tx = commit();
		assertSame(domain, TransactionUtil.getEditingDomain(tx));
		
		assertSame(domain, TransactionUtil.getEditingDomain(domain));
		
		IEditingDomainProvider edp = new IEditingDomainProvider() {
			public EditingDomain getEditingDomain() {
				return domain;
			}};
			
		assertSame(domain, TransactionUtil.getEditingDomain(edp));
	}
}
