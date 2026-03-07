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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.tests.fixtures.TestCommand;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.emf.transaction.util.ValidateEditSupport;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests validate-edit support.
 *
 * @author Christian W. Damus (cdamus)
 */
public class ValidateEditTest extends AbstractTest {

	private static final String newTitle = "New Title";

	private Book book;

	private final Command setTitle = new TestCommand() {
		@Override
		public boolean canExecute() {
			// command isn't executable if owner's resource is read-only
			return true;
		}

		public void execute() {
			try {
				book.setTitle(newTitle);
			} catch (Exception e) {
				fail(e);
			}
		}
	};

	private final Command clearTitle = new TestCommand() {
		@Override
		public boolean canExecute() {
			// command isn't executable if owner's resource is read-only
			return true;
		}

		public void execute() {
			try {
				book.setTitle(null);
			} catch (Exception e) {
				fail(e);
			}
		}
	};

	/**
	 * A control test for a scenario in which validateEdit will find all resources
	 * to be modifiable.
	 */
	@Test
	public void test_noValidateEditRequired() {
		try {
			getCommandStack().execute(setTitle, null);

			assertTitleChanged();
			assertResourceDirty();
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Simple unmodifiable resource scenario.
	 */
	public void ignore_test_validateEditRollback() {
		setResourceReadOnly();

		try {
			getCommandStack().execute(setTitle, null);

			Assert.fail("Should have rolled back");
		} catch (RollbackException e) {
			// success
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		} catch (Exception e) {
			fail(e);
		}

		assertTitleNotChanged();
		assertResourceNotDirty();
	}

	/**
	 * Custom validate-edit implementation.
	 */
	@Test
	public void test_customValidateEditSupport() {
		final boolean[] token = new boolean[1];

		setValidateEdit(new ValidateEditSupport.Default() {
			@Override
			protected IStatus doValidateEdit(Transaction transaction, Collection<? extends Resource> resources,
					Object context) {
				token[0] = true;
				return Status.CANCEL_STATUS;
			}
		});

		try {
			getCommandStack().execute(setTitle, null);

			Assert.fail("Should have rolled back");
		} catch (RollbackException e) {
			// success
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		} catch (Exception e) {
			fail(e);
		}

		assertTrue("Custom validation not invoked", token[0]);
		assertTitleNotChanged();
		assertResourceNotDirty();
	}

	/**
	 * Scenario in which validateEdit will find all resources to be modifiable but
	 * in which we also have a live validation failure.
	 */
	@Test
	public void test_liveValidationFailure_validateEditOK() {
		try {
			getCommandStack().execute(clearTitle, null);

			Assert.fail("Should have rolled back");
		} catch (RollbackException e) {
			// success
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Unmodifiable resource scenario in which we also have a live validation
	 * failure.
	 */
	@Test
	public void test_validationRollback_validateEditFails() {
		setResourceReadOnly();

		try {
			getCommandStack().execute(clearTitle, null);

			Assert.fail("Should have rolled back");
		} catch (RollbackException e) {
			// success
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		} catch (Exception e) {
			fail(e);
		}

		assertResourceNotDirty();
	}

	//
	// Fixture methods
	//

	@Override
	protected void doSetUp() throws Exception {

		super.doSetUp();

		// enable validation
		ValidationRollbackTest.validationEnabled = true;

		setValidateEdit(Boolean.TRUE);

		// default validate-edit implementation depends on mod tracking
		testResource.setTrackingModification(true);

		startReading();
		book = (Book) find("root/Root Book");
		commit();
		assertNotNull(book);
	}

	@Override
	protected void doTearDown() throws Exception {

		book = null;

		// disable validation
		ValidationRollbackTest.validationEnabled = false;

		super.doTearDown();
	}

	void setResourceReadOnly() {
		ResourceAttributes attr = new ResourceAttributes();
		attr.setReadOnly(true);

		try {
			file.setResourceAttributes(attr);
		} catch (CoreException e) {
			fail(e);
		}
	}

	void setValidateEdit(Object optionValue) {
		TransactionalEditingDomain.DefaultOptions defaults = TransactionUtil.getAdapter(domain,
				TransactionalEditingDomain.DefaultOptions.class);

		defaults.setDefaultTransactionOptions(Collections.singletonMap(Transaction.OPTION_VALIDATE_EDIT, optionValue));
	}

	void assertTitleChanged() {
		assertEquals(newTitle, book.getTitle());
	}

	void assertTitleNotChanged() {
		assertFalse(newTitle.equals(book.getTitle()));
	}

	void assertResourceDirty() {
		assertTrue("Resource not dirty", testResource.isModified());
	}

	void assertResourceNotDirty() {
		assertFalse("Resource is dirty", testResource.isModified());
	}
}
