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
 * $Id: ValidationRollbackTest.java,v 1.2 2006/01/30 19:47:50 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.impl.InternalTransaction;


/**
 * Tests transaction validation and rollback scenarios.
 *
 * @author Christian W. Damus (cdamus)
 */
public class ValidationRollbackTest extends AbstractTest {

	public static boolean validationEnabled = false;
	
	public ValidationRollbackTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(ValidationRollbackTest.class, "Validation and Rollback Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests that simple changes are rolled back.
	 */
	public void test_rollback() {
		try {
			Transaction xa = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			final String oldTitle = book.getTitle();
			final Writer oldAuthor = book.getAuthor();
			
			String newTitle = "New Title"; //$NON-NLS-1$
			Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
			assertNotNull(newAuthor);
			
			book.setTitle(newTitle);
			newAuthor.getBooks().add(book);  // change the inverse just for fun
			
			// make sure these changes actually took effect in the transaction
			assertSame(newTitle, book.getTitle());
			assertSame(newAuthor, book.getAuthor());
			
			xa.rollback();
			
			// check that the changes were rolled back
			domain.runExclusive(new Runnable() {
				public void run() {
					assertSame(oldTitle, book.getTitle());
					assertSame(oldAuthor, book.getAuthor());
				}});
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that simple changes are rolled back in a nested transaction.
	 */
	public void test_rollback_nested() {
		try {
			Transaction xa = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			// in the outer transaction, make some changes
			book.setTitle("Intermediate Title"); //$NON-NLS-1$
			Writer writer = (Writer) find("root/level1/level2/Level2 Writer"); //$NON-NLS-1$
			assertNotNull(writer);
			book.setAuthor(writer);
			
			// get the "old" values.  These were set by the outer transaction.
			//    They should be restored when the inner transaction rolls back
			final String oldTitle = book.getTitle();
			final Writer oldAuthor = book.getAuthor();
			
			// start an inner read/write transaction
			Transaction inner = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
			
			String newTitle = "New Title"; //$NON-NLS-1$
			Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
			assertNotNull(newAuthor);
			
			book.setTitle(newTitle);
			newAuthor.getBooks().add(book);  // change the inverse just for fun
			
			// make sure these changes actually took effect in the transaction
			assertSame(newTitle, book.getTitle());
			assertSame(newAuthor, book.getAuthor());
			
			inner.rollback();
			
			// check that the changes were rolled back to the outer transaction's
			//    values (not the very original values)
			assertSame(oldTitle, book.getTitle());
			assertSame(oldAuthor, book.getAuthor());
			
			xa.commit();
			
			// check that the outer transaction commit worked (values still ok)
			domain.runExclusive(new Runnable() {
				public void run() {
					assertSame(oldTitle, book.getTitle());
					assertSame(oldAuthor, book.getAuthor());
				}});
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that simple changes committed by an inner transaction are
	 * rolled back by the outer transaction.
	 */
	public void test_rollback_outer() {
		try {
			Transaction xa = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			// get the old values
			final String oldTitle = book.getTitle();
			final Writer oldAuthor = book.getAuthor();
			
			// start an inner read/write transaction
			Transaction inner = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
			
			String newTitle = "New Title"; //$NON-NLS-1$
			Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
			assertNotNull(newAuthor);
			
			book.setTitle(newTitle);
			newAuthor.getBooks().add(book);  // change the inverse just for fun
			
			inner.commit();
			
			// check that the changes were committed into the outer transaction
			assertSame(newTitle, book.getTitle());
			assertSame(newAuthor, book.getAuthor());
			
			xa.rollback();
			
			// check that the outer transaction rollback reverted the values
			domain.runExclusive(new Runnable() {
				public void run() {
					assertSame(oldTitle, book.getTitle());
					assertSame(oldAuthor, book.getAuthor());
				}});
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that validation automatically rolls back a transaction.
	 */
	public void test_validation() {
		Transaction xa = null;
		IStatus status = null;
		
		try {
			xa = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			book.setTitle(null);  // books must have titles
			
			xa.commit();  // this should throw RollbackException
			
			fail("Should have thrown RollbackException"); //$NON-NLS-1$
		} catch (RollbackException e) {
			// success
			status = e.getStatus();
			trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (Exception e) {
			fail(e);
		}
		
		assertNotNull(xa);
		assertNotNull(status);
		assertSame(status, xa.getStatus());
	}

	/**
	 * Tests that validation does not automatically roll back a nested
	 * transaction, but its changes are validated by the outer transaction.
	 */
	public void test_validation_nestedCommitted() {
		try {
			Transaction xa = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			Transaction inner = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
			
			book.setTitle(null);  // books must have titles
			
			try {
				inner.commit();  // this should not throw
			} catch (Exception e) {
				fail(e);
			}
			
			xa.commit();  // this should throw RollbackException
			
			fail("Should have thrown RollbackException"); //$NON-NLS-1$
		} catch (RollbackException e) {
			// success
			trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that validation excludes changes rolled back by a nested transaction.
	 */
	public void test_validation_nestedRolledBack() {
		try {
			Transaction xa = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			final String newTitle = "New Title";  //$NON-NLS-1$
			book.setTitle(newTitle);
			
			Transaction inner = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
			
			book.setTitle(null);  // books must have titles
			
			inner.rollback();  // only valid changes, now
			
			xa.commit();  // this should *not* throw RollbackException
			
			// check that the outer transaction's new value is committed
			domain.runExclusive(new Runnable() {
				public void run() {
					assertSame(newTitle, book.getTitle());
				}});
		} catch (Exception e) {
			fail(e);
		}
	}

	/**
	 * Tests that aborting a transaction correctly sets its status and causes
	 * it to rollback on commit.
	 */
	public void test_abort() {
		Transaction xa = null;
		IStatus status = Status.CANCEL_STATUS;
		
		try {
			xa = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
			
			((InternalTransaction) xa).abort(status);
			
			xa.commit();  // this should throw RollbackException
			
			fail("Should have thrown RollbackException"); //$NON-NLS-1$
		} catch (RollbackException e) {
			// success
			trace("Got expected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
			
			assertSame(status, e.getStatus());
		} catch (Exception e) {
			fail(e);
		}
		
		assertNotNull(xa);
		assertSame(status, xa.getStatus());
	}
	
	//
	// Fixture methods
	//
	
	protected void doSetUp()
		throws Exception {
		
		super.doSetUp();
		
		// enable validation
		validationEnabled = true;
	}
	
	protected void doTearDown()
		throws Exception {
		
		// disable validation
		validationEnabled = false;
		
		super.doTearDown();
	}
}
