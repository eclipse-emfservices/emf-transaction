/**
 * Copyright (c) 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.tests.fixtures.TestValidationEditingDomain;

/**
 * Tests validator creation during transaction editing domain commit
 *
 * @author David Cummings (dcummin)
 */
public class EditingDomainValidatorTest extends AbstractTest {

	private static final String TEST_DOMAIN1 = "org.eclipse.emf.transaction.tests.TestValidationDomain1"; //$NON-NLS-1$
	
	private static final TransactionalEditingDomain myDomain = TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(TEST_DOMAIN1);
	
	public EditingDomainValidatorTest(String name) {
		super(name);
	}
	
	public static Test suite() {
		return new TestSuite(EditingDomainValidatorTest.class, "Editing Domain Validator Tests"); //$NON-NLS-1$
	}
	
	/** May be overridden by subclasses to create non-default editing domains. */
	@Override
	protected TransactionalEditingDomain createEditingDomain(ResourceSet rset) {
		return myDomain;
	}
	
	@Override
	protected ResourceSet createResourceSet() {
		return myDomain.getResourceSet();
	}
	
	/**
	 * Tests overriding of validators in editing domain
	 */
	public void test_createValidators_177643() {	
		TestValidationEditingDomain.enableCustomValidator = true;
		
		startWriting();
		Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		book.setTitle("New Title"); //$NON-NLS-1$
		commit();
		assertEquals(1, TestValidationEditingDomain.readWriteValidatorHitCount);
		
		TestValidationEditingDomain.enableCustomValidator = false;
	}
}
