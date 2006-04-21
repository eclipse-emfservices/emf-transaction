/**
 * <copyright>
 *
 * Copyright (c) 2006 IBM Corporation and others.
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
 * $Id: TransactionUtilTests.java,v 1.3 2006/04/21 18:03:41 cdamus Exp $
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
		assertSame(domain, TransactionUtil.getEditingDomain((Object) tx));
		
		assertSame(domain, TransactionUtil.getEditingDomain((Object) domain));
		
		IEditingDomainProvider edp = new IEditingDomainProvider() {
			public EditingDomain getEditingDomain() {
				return domain;
			}};
			
		assertSame(domain, TransactionUtil.getEditingDomain((Object) edp));
	}
}
