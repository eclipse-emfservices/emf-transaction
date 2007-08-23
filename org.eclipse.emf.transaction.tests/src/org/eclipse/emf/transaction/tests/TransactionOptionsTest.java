/**
 * <copyright>
 *
 * Copyright (c) 2005, 2007 IBM Corporation and others.
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
 * $Id: TransactionOptionsTest.java,v 1.7.2.1 2007/08/23 19:40:31 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.util.Collection;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.impl.InternalTransaction;
import org.eclipse.emf.transaction.impl.TransactionImpl;
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

		assertTrue(tx.getChangeDescription().isEmpty());
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
	
	public void test_transactionOptionInheritance_135569() {
		Map options = new java.util.HashMap();
		Map active;
		
		// test the inheritance of empty options
		startWriting();
		active = getActiveTransaction().getOptions();
		
		assertTrue(getActiveTransaction().getOptions().isEmpty());
		
		startWriting();
		active = getActiveTransaction().getOptions();
		
		assertTrue(getActiveTransaction().getOptions().isEmpty());

		commit();
		commit();
		
		// test the inheritance of non-empty options
		options.put(Transaction.OPTION_NO_UNDO, Boolean.TRUE);
		Object marker = new Object();
		options.put("my own option", marker); //$NON-NLS-1$
		
		startWriting(options);
		active = getActiveTransaction().getOptions();
		
		assertSame(Boolean.TRUE, active.get(Transaction.OPTION_NO_UNDO));
		assertSame(marker, active.get("my own option")); //$NON-NLS-1$
		
		startWriting();
		active = getActiveTransaction().getOptions();
		
		assertSame(Boolean.TRUE, active.get(Transaction.OPTION_NO_UNDO));
		assertSame(marker, active.get("my own option")); //$NON-NLS-1$
		
		commit();
		commit();
		
		// test the overriding of options
		
		options.put(Transaction.OPTION_NO_UNDO, Boolean.TRUE);
		marker = new Object();
		options.put("my own option", marker); //$NON-NLS-1$
		
		startWriting(options);
		
		options.put(Transaction.OPTION_NO_UNDO, Boolean.FALSE);
		Object marker2 = new Object();
		options.put("my own option", marker2); //$NON-NLS-1$
		
		// active options should be copied, not affected by changes to 'options'
		active = getActiveTransaction().getOptions();
		
		assertSame(Boolean.TRUE, active.get(Transaction.OPTION_NO_UNDO));
		assertSame(marker, active.get("my own option")); //$NON-NLS-1$
		
		startWriting(options);
		active = getActiveTransaction().getOptions();
		
		assertSame(Boolean.FALSE, active.get(Transaction.OPTION_NO_UNDO));
		assertSame(marker2, active.get("my own option")); //$NON-NLS-1$
		
		commit();
		commit();
	}
	
    /**
     * Tests that a root-level transaction's notifications are not retained
     * after it commits. This is important because the transaction in question
     * may linger for some time in a command stack or operation history.
     */
    public void test_notificationsNotRetainedAfterCommit_152335() {
        startWriting();
        InternalTransaction tx = getActiveTransaction();
        
        final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
        assertNotNull(book);
        
        final String newTitle = "New Title"; //$NON-NLS-1$
        final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
        assertNotNull(newAuthor);
        
        book.setTitle(newTitle);
        newAuthor.getBooks().add(book);
        
        // at this point, we still have notifications
        assertFalse(tx.getNotifications().isEmpty());
        
        commit();
        
        // but not any more
        assertTrue(tx.getNotifications().isEmpty());
    }
    
    /**
     * Tests that a silent unprotected transaction never accumulates
     * notifications, since they are not needed for anything.
     */
    public void test_noNotificationsInSilentUnprotected_152335() {
        Map options = new java.util.HashMap();
        options.put(Transaction.OPTION_NO_NOTIFICATIONS, Boolean.TRUE);
        options.put(Transaction.OPTION_UNPROTECTED, Boolean.TRUE);
        
        startWriting(options);
        InternalTransaction tx = getActiveTransaction();
        
        final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
        assertNotNull(book);
        
        final String newTitle = "New Title"; //$NON-NLS-1$
        final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
        assertNotNull(newAuthor);
        
        book.setTitle(newTitle);
        newAuthor.getBooks().add(book);
        
        // we did not gather notifications
        assertTrue(tx.getNotifications().isEmpty());
        
        commit();
        
        // so, of course we still don't have any
        assertTrue(tx.getNotifications().isEmpty());
    }
    
    /**
     * Tests that when a silent unprotected transaction has children that think
     * they are collecting notifications, they do not, but notifications for
     * ancestor transactions are not lost.
     */
    public void test_childrenOfSilentUnprotected_152332() {
		TestListener listener = new TestListener();
		domain.addResourceSetListener(listener);
		
		startWriting();
		
        final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
        assertNotNull(book);
        
        final String newTitle = "New Title"; //$NON-NLS-1$
        book.setTitle(newTitle);
        
        Map options = new java.util.HashMap();
        options.put(Transaction.OPTION_NO_NOTIFICATIONS, Boolean.TRUE);
        options.put(Transaction.OPTION_UNPROTECTED, Boolean.TRUE);
        
        // intervening silent-unprotected
        startWriting(options);
        
        // this transaction thinks it is normal, but it isn't
        startWriting();
        
        final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
        assertNotNull(newAuthor);
        
        newAuthor.getBooks().add(book);
        
        commit();  // the "normal" child
        
        commit();  // the silent, unprotected
        
        commit();  // the root transaction
        
        // check that the book's title and author were both changed
        assertEquals(newTitle, book.getTitle());
        assertSame(newAuthor, book.getAuthor());
        
        // check that we got the notification for the book's title changing,
        //    and only that notification
        assertNotNull(listener.postcommitNotifications);
        assertEquals(1, listener.postcommitNotifications.size());
        Notification notification = (Notification) listener.postcommitNotifications.get(0);
        assertSame(EXTLibraryPackage.Literals.BOOK__TITLE, notification.getFeature());
    }
    
    public void test_optionsInheritedAtActivationTime_() {
        final Object sync = new Object();
        final String bogusOption = "**bogus**option**"; //$NON-NLS-1$
        
        Runnable run = new Runnable() {
            public void run() {
                synchronized (sync) {
                    startWriting(bogusOption);
                    
                    sync.notifyAll();
                    
                    try {
                        sync.wait();
                    } catch (Exception e) {
                        fail("Wait failed in thread"); //$NON-NLS-1$
                    }
                    
                    commit();
                    
                    try {
                        sync.notifyAll();
                    } catch (Exception e) {
                        fail("Wait failed in thread"); //$NON-NLS-1$
                    }
                    
                }
            }};
        
        synchronized (sync) {
            Thread t = new Thread(run);
            t.setDaemon(true);
            t.start();
            
            try {
                sync.wait();
            } catch (Exception e) {
                fail("Wait failed on main"); //$NON-NLS-1$
            }
        }
        
        Transaction tx = new TransactionImpl(domain, false, null);
        
        assertFalse(tx.getOptions().containsKey(bogusOption));
        
        synchronized (sync) {
            // let the thread commit and die
            sync.notifyAll();
            
            try {
                sync.wait();
            } catch (Exception e) {
                fail("Wait failed on main"); //$NON-NLS-1$
            }
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
