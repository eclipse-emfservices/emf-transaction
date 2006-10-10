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
 * $Id: ValidationRollbackTest.java,v 1.4 2006/10/10 14:31:40 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.DemultiplexingListener;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TriggerListener;
import org.eclipse.emf.transaction.impl.InternalTransaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.tests.fixtures.LogCapture;


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
	
	/**
	 * Tests that, when an exception unwinds the Java stack during the execution
	 * of a Command, the active transactions are rolled back in
	 * the correct sequence.
	 */
	public void test_rollbackNestingTransactionOnException_135673() {
		Command command = new RecordingCommand(domain, "") { //$NON-NLS-1$
			public boolean canUndo() { return true; }
			protected void doExecute() {
				// start some nested transactions
				try {
					((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
					((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
				} catch (Exception e) {
					fail("Failed to start nested transaction: " + e.getLocalizedMessage()); //$NON-NLS-1$
				}
				throw new TestError("intentional error"); //$NON-NLS-1$
			}
		};
		
		try {
			getCommandStack().execute(command);
		} catch (TestError error) {
			// success case -- error was not masked by IllegalStateException
		} catch (IllegalArgumentException e) {
			fail("Rolled back out of order: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}
	}
	
    /**
     * Test that failure in the deactivation of a transaction does not cause
     * the lock to remain held.
     */
    public void test_danglingTransactionOnException_149340() {
        final Error error = new Error();
        
        ResourceSetListener testListener = new DemultiplexingListener() {
            protected void handleNotification(TransactionalEditingDomain domain, Notification notification) {
            	throw error;
            }};
        
        try {
            domain.addResourceSetListener(testListener);
            
            try {
                domain.getCommandStack().execute(new RecordingCommand(domain) {
                    protected void doExecute() {
                        root.getWriters().clear();
                        root.getStock().clear();
                        root.getBranches().clear();
                    }});
                fail("Should have thrown Error"); //$NON-NLS-1$
            } catch (Error e) {
                assertSame(error, e);
            }
            
            // check that the domain has no dangling transaction
            class TestThread implements Runnable {
                boolean ran = false;
                
                public void run() {
                    // get the transaction lock
                    try {
                        domain.runExclusive(new Runnable() {
                            public void run() {/* nothing to do */}
                        });
                    } catch (InterruptedException ex) {
                        fail("Interrupted"); //$NON-NLS-1$
                    }
                    
                    synchronized (this) {
                        ran = true;
                        notifyAll();
                    }
                }
            }
            
            TestThread testThread = new TestThread();
            synchronized (testThread) {
                Thread t = new Thread(testThread);
                t.setDaemon(true);
                t.start();
                
                // 2 seconds should be plenty sufficient to get the lock
                try {
                    testThread.wait(2000);
                } catch (InterruptedException ex) {
                    fail("Interrupted"); //$NON-NLS-1$
                }
                
                assertTrue("Dangling transaction lock", testThread.ran); //$NON-NLS-1$
            }
        } finally {
            domain.removeResourceSetListener(testListener);
        }
    }
	
    /**
     * Test that run-time exceptions in a trigger command cause rollback of
     * the whole transaction.
     */
    public void test_triggerRollback_146853() {
        final RuntimeException error = new RuntimeException();
        
        ResourceSetListener testListener = new TriggerListener() {
        	protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        LogCapture logCapture = new LogCapture(
        		getCommandStack(), EMFTransactionPlugin.getPlugin().getBundle());
            
        try {
            domain.addResourceSetListener(testListener);
            
            domain.getCommandStack().execute(new RecordingCommand(domain) {
                protected void doExecute() {
                    root.getWriters().clear();
                    root.getStock().clear();
                    root.getBranches().clear();
                }});
            
            // verify that the exception was duly logged
            logCapture.assertLogged(error);
            
            // verify that rollback occurred
            assertFalse(root.getWriters().isEmpty());
            assertFalse(root.getStock().isEmpty());
            assertFalse(root.getBranches().isEmpty());
        } finally {
            logCapture.stop();
            domain.removeResourceSetListener(testListener);
        }
    }
	
    /**
     * Test that OperationCanceledException in a trigger command causes
     * rollback of the whole transaction, without any log message (because it
     * is a normal condition).
     */
    public void test_triggerRollback_cancel_146853() {
        final RuntimeException error = new OperationCanceledException();
        
        ResourceSetListener testListener = new TriggerListener() {
        	protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        LogCapture logCapture = new LogCapture(
        		getCommandStack(), EMFTransactionPlugin.getPlugin().getBundle());
            
        try {
            domain.addResourceSetListener(testListener);
            
            domain.getCommandStack().execute(new RecordingCommand(domain) {
                protected void doExecute() {
                    root.getWriters().clear();
                    root.getStock().clear();
                    root.getBranches().clear();
                }});
            
            // verify that the exception was *not* logged
            IStatus log = logCapture.getLastLog();
            assertNull(log);
            
            // verify that rollback occurred
            assertFalse(root.getWriters().isEmpty());
            assertFalse(root.getStock().isEmpty());
            assertFalse(root.getBranches().isEmpty());
        } finally {
            logCapture.stop();
            domain.removeResourceSetListener(testListener);
        }
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
	
	static class TestError extends Error {
		private static final long serialVersionUID = 1502966836790504386L;

		TestError(String msg) {
			super(msg);
		}
	}
}
