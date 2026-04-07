/**
 * Copyright (c) 2007, 2026 IBM Corporation and others.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.tests.fixtures.TestValidationEditingDomain;
import org.junit.jupiter.api.Test;

/**
 * Tests validator creation during transaction editing domain commit
 *
 * @author David Cummings (dcummin)
 */
public class EditingDomainValidatorTest extends AbstractTest {

	private static final String TEST_DOMAIN1 = "org.eclipse.emf.transaction.tests.TestValidationDomain1";

	private static final TransactionalEditingDomain myDomain = TransactionalEditingDomain.Registry.INSTANCE.getEditingDomain(TEST_DOMAIN1);


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
	@Test
	public void test_createValidators_177643() {
		TestValidationEditingDomain.enableCustomValidator.set(true);
		int initialCount = TestValidationEditingDomain.readWriteValidatorHitCount.get();

		startWriting();
		Book book = (Book) find("root/Root Book");
		book.setTitle("New Title");
		commit();
		assertEquals(initialCount + 1, TestValidationEditingDomain.readWriteValidatorHitCount.get());

		TestValidationEditingDomain.enableCustomValidator.set(false);
	}
}
