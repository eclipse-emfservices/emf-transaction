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
 * $Id: TransactionUtilTests.java,v 1.2 2006/04/21 14:59:17 cdamus Exp $
 */
package org.eclipse.emf.transaction.util.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
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
	
	/**
	 * Tests the <code>getProperContents(EObject)</code> method.
	 */
	public void test_getProperContents() {
		EPackage rootPackage = EcoreFactory.eINSTANCE.createEPackage();
		rootPackage.setName("root"); //$NON-NLS-1$
		
		// proper contents consistent with EContents when no cross-resource
		//   containment is involved
		
		EList contents = rootPackage.eContents();
		EList properContents = TransactionUtil.getProperContents(rootPackage);
		
		EClass eclass = EcoreFactory.eINSTANCE.createEClass();
		eclass.setName("Foo"); //$NON-NLS-1$
		rootPackage.getEClassifiers().add(eclass);
		
		assertTrue(contents.contains(eclass));
		assertTrue(properContents.equals(contents));
		
		EPackage nested = EcoreFactory.eINSTANCE.createEPackage();
		nested.setName("nested"); //$NON-NLS-1$
		rootPackage.getESubpackages().add(nested);
		
		assertTrue(contents.contains(eclass));
		assertTrue(contents.contains(nested));
		assertTrue(properContents.equals(contents));
		
		ResourceSet rset = new ResourceSetImpl();
		Resource res1 = rset.createResource(URI.createURI("null:///res1.ecore")); //$NON-NLS-1$
		Resource res2 = rset.createResource(URI.createURI("null:///res2.ecore")); //$NON-NLS-1$
		
		res1.getContents().add(rootPackage);
		res2.getContents().add(nested);  // cross-resource-contained
		
		// the nested package is no longer properly contained
		
		assertTrue(contents.contains(nested));
		assertFalse(properContents.contains(nested));
		assertFalse(properContents.equals(contents));
		assertTrue(properContents.contains(eclass));
		
		res2.unload();
		
		assertTrue(nested.eIsProxy());
		
		assertTrue(contents.contains(nested));
		assertFalse(properContents.contains(nested));
		assertFalse(properContents.equals(contents));
		assertTrue(properContents.contains(eclass));
	}
}
