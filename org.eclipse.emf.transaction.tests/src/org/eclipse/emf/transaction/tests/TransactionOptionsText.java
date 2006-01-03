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
 * $Id: TransactionOptionsText.java,v 1.1 2006/01/03 20:51:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TXCommandStack;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.tests.fixtures.TestListener;


/**
 * Tests the effect of transaction options.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TransactionOptionsText extends AbstractTest {

	public TransactionOptionsText(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(TransactionOptionsText.class, "Transaction Options Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests that the <code>OPTION_NO_NOTIFICATIONS</code> results in post-commit
	 * listeners not being notified of changes performed by a write transaction.
	 */
	public void test_noNotifications() {
		TestListener listener = new TestListener();
		domain.addResourceSetListener(listener);
		
		startWriting(TXCommandStack.OPTION_NO_NOTIFICATIONS);
		
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
		
		startWriting(TXCommandStack.OPTION_NO_TRIGGERS);
		
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
			
			startWriting(TXCommandStack.OPTION_NO_VALIDATION);
			
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
		startWriting(TXCommandStack.OPTION_NO_UNDO);
		
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
	 * <code>RecordingCommand</code> not being undoable.
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
			getCommandStack().execute(cmd, makeOptions(TXCommandStack.OPTION_NO_UNDO));
		} catch (Exception e) {
			fail(e);
		}
		
		assertFalse(getCommandStack().canUndo());
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
			getCommandStack().execute(cmd, makeOptions(TXCommandStack.OPTION_NO_UNDO));
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
		startWriting(TXCommandStack.OPTION_UNPROTECTED);
		
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
		assertNotNull(listener.postcommit.getNotifications());
		
		// one notification from the book title, one from the book author,
		//    and one from the author books
		assertEquals(3, listener.postcommit.getNotifications().size());
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
