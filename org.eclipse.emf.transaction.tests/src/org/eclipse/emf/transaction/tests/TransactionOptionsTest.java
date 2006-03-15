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
 * $Id: TransactionOptionsTest.java,v 1.3 2006/03/15 01:40:26 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.util.Collection;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.tests.fixtures.TestListener;


/**
 * Tests the effect of transaction options.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TransactionOptionsTest extends AbstractTest {

	public TransactionOptionsTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(TransactionOptionsTest.class, "Transaction Options Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests that the <code>OPTION_NO_NOTIFICATIONS</code> results in post-commit
	 * listeners not being notified of changes performed by a write transaction.
	 */
	public void test_noNotifications() {
		TestListener listener = new TestListener();
		domain.addResourceSetListener(listener);
		
		startWriting(Transaction.OPTION_NO_NOTIFICATIONS);
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		final String newTitle = "New Title"; //$NON-NLS-1$
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		book.setTitle(newTitle);
		newAuthor.getBooks().add(book);
		
		commit();
		
		assertNull(listener.postcommit);
	}

	/**
	 * Tests that the <code>OPTION_NO_TRIGGERS</code> results in pre-commit
	 * listeners not being invoked to produce trigger commands.
	 */
	public void test_noTriggers() {
		TestListener listener = new TestListener();
		domain.addResourceSetListener(listener);
		
		startWriting(Transaction.OPTION_NO_TRIGGERS);
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		final String newTitle = "New Title"; //$NON-NLS-1$
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		book.setTitle(newTitle);
		newAuthor.getBooks().add(book);
		
		commit();
		
		assertNull(listener.precommit);
	}

	/**
	 * Tests that the <code>OPTION_NO_VALIDATION</code> results in the write
	 * transaction not being validated.
	 */
	public void test_noValidation() {
		try {
			ValidationRollbackTest.validationEnabled = true;
			
			startWriting(Transaction.OPTION_NO_VALIDATION);
			
			final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
			assertNotNull(book);
			
			book.setTitle(null);
			
			commit();

			startReading();
			
			// would not be allowed if we rolled back.  Besides, the commit()
			//    call above would have thrown anyway
			assertNull(book.getTitle());
			
			commit();
		} finally {
			ValidationRollbackTest.validationEnabled = false;
		}
	}

	/**
	 * Tests that the <code>OPTION_NO_UNDO</code> results in the write action
	 * not recording undo information.
	 */
	public void test_noUndo() {
		startWriting(Transaction.OPTION_NO_UNDO);
		
		final Transaction tx = getActiveTransaction();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		final String newTitle = "New Title"; //$NON-NLS-1$
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		book.setTitle(newTitle);
		newAuthor.getBooks().add(book);
		
		commit();

		assertNull(tx.getChangeDescription());
	}

	/**
	 * Tests that the <code>OPTION_NO_UNDO</code> results in a
	 * <code>RecordingCommand</code> not doing anything when undone.
	 */
	public void test_noUndo_recordingCommand() {
		Command cmd = new RecordingCommand(domain) {
			protected void doExecute() {
				final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
				assertNotNull(book);
				
				final String newTitle = "New Title"; //$NON-NLS-1$
				final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
				assertNotNull(newAuthor);
				
				book.setTitle(newTitle);
				newAuthor.getBooks().add(book);
			}};

		try {
			getCommandStack().execute(cmd, makeOptions(Transaction.OPTION_NO_UNDO));
		} catch (Exception e) {
			fail(e);
		}
		
		assertTrue(getCommandStack().canUndo());
		
		getCommandStack().undo();
		
		startReading();
		
		// still find these changes
		Book book = (Book) find("root/New Title"); //$NON-NLS-1$
		assertNotNull(book.getTitle());
		assertSame(find("root/level1/Level1 Writer"), book.getAuthor()); //$NON-NLS-1$
		
		commit();
	}

	/**
	 * Tests that the <code>OPTION_NO_UNDO</code> does not have any effect on
	 * the undoability of regular EMF commands.
	 */
	public void test_noUndo_regularCommand() {
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
				
		final String newTitle = "New Title"; //$NON-NLS-1$

		Command cmd = new SetCommand(
				domain, book, EXTLibraryPackage.eINSTANCE.getBook_Title(), newTitle);
		
		commit();

		try {
			getCommandStack().execute(cmd, makeOptions(Transaction.OPTION_NO_UNDO));
		} catch (Exception e) {
			fail(e);
		}
		
		assertTrue(getCommandStack().canUndo());
	}
	
	/**
	 * Tests that the <code>OPTION_UNPROTECTED</code> results in write transactions
	 * being permitted during reads.
	 */
	public void test_unprotected() {
		TestListener listener = new TestListener();
		domain.addResourceSetListener(listener);
		
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		final String newTitle = "New Title"; //$NON-NLS-1$
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		// start an unprotected write transaction
		startWriting(Transaction.OPTION_UNPROTECTED);
		
		// make changes
		book.setTitle(newTitle);
		newAuthor.getBooks().add(book);
		
		// commit the write
		commit();
		
		assertSame(newTitle, book.getTitle());
		assertSame(newAuthor, book.getAuthor());
		
		commit();
		
		// listeners are notified of the changes
		assertNotNull(listener.postcommit);
		assertNotNull(listener.postcommitNotifications);
		
		// one notification from the book title, one from the book author,
		//    and one from the author books
		assertEquals(3, listener.postcommitNotifications.size());
	}
	
	/**
	 * Tests that notifications from nested silent transactions are correctly
	 * omitted from the post-commit event.
	 */
	public void test_nested_noNotifications_124334() {
		TestListener listener = new TestListener();
		domain.addResourceSetListener(listener);
		
		startWriting();
		
		// nested silent transaction
		startWriting(Transaction.OPTION_NO_NOTIFICATIONS);
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		final String newTitle = "New Title"; //$NON-NLS-1$
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		book.setTitle(newTitle);
		newAuthor.getBooks().add(book);
		
		commit();
		
		commit();
		
		assertNull(listener.postcommit);
	}
	
	/**
	 * Tests that notifications from nested unvalidated transactions are included
	 * in the post-commit event (i.e., unvalidated does not imply silent).
	 */
	public void test_nested_unvalidatedPostCommit_124334() {
		TestListener listener = new TestListener();
		domain.addResourceSetListener(listener);
		
		startWriting();
		
		// nested silent transaction
		startWriting(Transaction.OPTION_NO_VALIDATION);
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		final String newTitle = null;
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		book.setTitle(newTitle);
		newAuthor.getBooks().add(book);
		
		commit();
		
		// succeeds because there is no validation of the null book title
		commit();
		
		assertNotNull(listener.postcommit);
	}
	
	/**
	 * Tests that nested unvalidated transactions are not validated, but their
	 * outer transactions are.
	 */
	public void test_nested_unvalidated_124334() {
		startWriting();
		
		Transaction tx = getActiveTransaction();
		
		// nested silent transaction
		startWriting(Transaction.OPTION_NO_VALIDATION);
		
		final Book book1 = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book1);
		
		final Book book2 = (Book) find("root/level1/Level1 Book"); //$NON-NLS-1$
		assertNotNull(book2);
		
		final String newTitle = null;
		
		book1.setTitle(newTitle);
		
		commit();
		
		book2.setTitle(newTitle);
		
		try {
			tx.commit();
			
			fail("Should have rolled back because of outer transaction validation"); //$NON-NLS-1$
		} catch (RollbackException e) {
			// expected exception
			Collection errors = findValidationStatuses(
					e.getStatus(), IStatus.ERROR);
			
			// the inner transaction's error was not detected
			assertEquals(1, errors.size());
		}
	}
	
	//
	// Fixture methods
	//
	
	protected void doSetUp()
		throws Exception {
		
		super.doSetUp();
		
		// in case the resource that we created tracks modification, we
		//    don't want this because it will add notifications that
		//    confuse our counts when we attach listeners to gather
		//    notifications
		testResource.setTrackingModification(false);
		
		// enable validation
		ValidationRollbackTest.validationEnabled = true;
	}
	
	protected void doTearDown()
		throws Exception {
		
		// disable validation
		ValidationRollbackTest.validationEnabled = false;
		
		super.doTearDown();
	}
}
