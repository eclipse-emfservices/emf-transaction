/**
 * <copyright>
 *
 * Copyright (c) 2005, 2008 IBM Corporation and others.
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
 * $Id: ValidationRollbackTest.java,v 1.10 2008/05/06 15:04:08 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Random;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.AbortExecutionException;
import org.eclipse.emf.common.command.AbstractCommand;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CommandStack;
import org.eclipse.emf.common.command.CommandStackListener;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.DemultiplexingListener;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.ResourceSetListenerImpl;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TriggerListener;
import org.eclipse.emf.transaction.impl.InternalTransaction;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.internal.EMFTransactionPlugin;
import org.eclipse.emf.transaction.tests.fixtures.LogCapture;
import org.eclipse.emf.transaction.tests.fixtures.TestListener;


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
			@Override
			public boolean canUndo() { return true; }
			@Override
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
            @Override
			protected void handleNotification(TransactionalEditingDomain domain, Notification notification) {
            	throw error;
            }};
        
        try {
            domain.addResourceSetListener(testListener);
            
            try {
                domain.getCommandStack().execute(new RecordingCommand(domain) {
                    @Override
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
        	@Override
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			@Override
					protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        LogCapture logCapture = new LogCapture(
        		getCommandStack(), EMFTransactionPlugin.getPlugin().getBundle());
            
        try {
            domain.addResourceSetListener(testListener);
            
            domain.getCommandStack().execute(new RecordingCommand(domain) {
                @Override
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
        	@Override
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
        		return new RecordingCommand(domain, "Error") { //$NON-NLS-1$
        			@Override
					protected void doExecute() {
        				throw error;
        			}};
        	}};
        
        LogCapture logCapture = new LogCapture(
        		getCommandStack(), EMFTransactionPlugin.getPlugin().getBundle());
            
        try {
            domain.addResourceSetListener(testListener);
            
            domain.getCommandStack().execute(new RecordingCommand(domain) {
                @Override
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
    
    /**
     * Tests that, when a command execution is rolled back, the command stack
     * listeners are notified again that the stack is changed, so that they
     * will correctly update themselves if necessary.
     */
    public void test_rollbackNotifiesCommandStackListeners_175725() {
        class TestCSL implements CommandStackListener {
            int invocationCount = 0;
            public void commandStackChanged(EventObject event) {
                invocationCount++;
            }
        }
        
        TestCSL listener = new TestCSL();
        CommandStack stack = domain.getCommandStack();
        stack.addCommandStackListener(listener);
        
        final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
        assertNotNull(book);
        Command command = SetCommand.create(
            domain, book, EXTLibraryPackage.Literals.BOOK__TITLE, null);
        
        try {
            stack.execute(command);  // validation fails on null title
        } catch (Exception e) {
            fail(e);
        } finally {
            stack.removeCommandStackListener(listener);
        }
        
        assertEquals("Command-stack listener invoked wrong number of times", //$NON-NLS-1$
            2, listener.invocationCount);
        assertFalse("Should not have an undo command", stack.canUndo()); //$NON-NLS-1$
    }
    
    /**
     * Tests that, when a command execution fails with a run-time exception,
     * the transaction is rolled back and the stack does not attempt to commit
     * it.
     */
    public void test_rollbackOnRuntimeException_185040() {
        final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
        assertNotNull(book);
        Command command = new RecordingCommand(domain) {
            @Override
			protected void doExecute() {
                book.setTitle("New Title"); //$NON-NLS-1$
                throw new NullPointerException();
            }};
        
        try {
            getCommandStack().execute(command, Collections.EMPTY_MAP);
            fail("Should have rolled back"); //$NON-NLS-1$
        } catch (RollbackException e) {
            // expected
            System.out.println("Got expected rollback"); //$NON-NLS-1$
            
            // check that rollback was effective
            assertEquals("Root Book", book.getTitle()); //$NON-NLS-1$
        } catch (InterruptedException e) {
            fail("Interrupted"); //$NON-NLS-1$
        }
    }

    /**
     * Tests that rollback of a nesting transaction does not result in listeners
     * being notified of the changes committed by its nested children, that were
     * subsequently rolled back by it.
     */
    public void test_rollback_nesting_noNotifications_206819() {
        TestListener l = new TestListener(NotificationFilter.NOT_TOUCH);
        domain.addResourceSetListener(l);
        
        try {
            // get old values
            startReading();
            final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
            assertNotNull(book);
            final String oldTitle = book.getTitle();
            final Writer oldAuthor = book.getAuthor();
            commit();
            
            l.reset();
            
            Transaction xa = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
            
            // in the outer transaction, make some changes
            book.setTitle("Intermediate Title"); //$NON-NLS-1$
            Writer writer = (Writer) find("root/level1/level2/Level2 Writer"); //$NON-NLS-1$
            assertNotNull(writer);
            book.setAuthor(writer);
            
            // start an inner read/write transaction
            Transaction inner = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
            
            String newTitle = "New Title"; //$NON-NLS-1$
            Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
            assertNotNull(newAuthor);
            
            book.setTitle(newTitle);
            newAuthor.getBooks().add(book);  // change the inverse just for fun
            
            inner.commit();
            
            xa.rollback();
            
            List<Notification> notifications = l.postcommitNotifications;
            
            // check that rollback worked
            startReading();
            assertSame(oldTitle, book.getTitle());
            assertSame(oldAuthor, book.getAuthor());
            commit();
            
            if (notifications != null) {
                fail("Got " + notifications.size() + " post-commit notifications");  //$NON-NLS-1$//$NON-NLS-2$
            }
        } catch (Exception e) {
            fail(e);
        } finally {
            domain.removeResourceSetListener(l);
        }
    }

    /**
     * Tests that rollback of a nesting transaction does not result in
     * concurrent modification exceptions when processing trailing notifications
     * (those that occurred in a parent transaction after the last child
     * committed).
     */
    public void test_rollback_nesting_trailingNotifications_206819() {
        TestListener l = new TestListener(NotificationFilter.NOT_TOUCH);
        domain.addResourceSetListener(l);
        
        try {
            // get old values
            startReading();
            final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
            assertNotNull(book);
            final String oldTitle = book.getTitle();
            final Writer oldAuthor = book.getAuthor();
            commit();
            
            l.reset();
            
            Transaction xa = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
            
            // in the outer transaction, make some changes
            book.setTitle("Intermediate Title"); //$NON-NLS-1$
            Writer writer = (Writer) find("root/level1/level2/Level2 Writer"); //$NON-NLS-1$
            assertNotNull(writer);
            book.setAuthor(writer);
            
            // start an inner read/write transaction
            Transaction inner = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
            
            String newTitle = "New Title"; //$NON-NLS-1$
            Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
            assertNotNull(newAuthor);
            
            book.setTitle(newTitle);
            newAuthor.getBooks().add(book);  // change the inverse just for fun
            
            Transaction inner2 = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
            
            book.setTitle("Something else"); //$NON-NLS-1$
            
            inner2.commit();
            
            book.setTitle("Something else again"); //$NON-NLS-1$
            
            inner.commit();
            
            book.setTitle("Yet another something"); //$NON-NLS-1$
            
            xa.rollback();
            
            List<Notification> notifications = l.postcommitNotifications;
            
            // check that rollback worked
            startReading();
            assertSame(oldTitle, book.getTitle());
            assertSame(oldAuthor, book.getAuthor());
            commit();
            
            if (notifications != null) {
                fail("Got " + notifications.size() + " post-commit notifications");  //$NON-NLS-1$//$NON-NLS-2$
            }
        } catch (Exception e) {
            fail(e);
        } finally {
            domain.removeResourceSetListener(l);
        }
    }

    /**
     * Tests that when a rollback transaction creates nested transactions, we do not
     * end up attempting illegal sub-lists of notification lists in the validator when
     * reducing the notifications leaves some that indicate non-undoable changes (such
     * as resource loads and URI changes).
     */
    public void test_rollback_nestedTransactions_illegalSubList_227429() {
        // add a post-commit listener to ensure that we will attempt to send
        // post-commit events
        TestListener tl = new TestListener(NotificationFilter.NOT_TOUCH);
        domain.addResourceSetListener(tl);
        
        final boolean[] enable = new boolean[] {false};
        final Writer[] writer = new Writer[1];
        final Random random = new Random(System.currentTimeMillis());
        
        // encapsulated operation that has non-undoable side-effects
        final Runnable run = new Runnable() {
            Resource resource = null;
            public void run() {
                if (resource == null) {
                    resource = domain.getResourceSet().createResource(
                            URI.createURI("http://localhost/foo.xmi")); //$NON-NLS-1$
                }
                
                Book book = EXTLibraryFactory.eINSTANCE.createBook();
                book.setTitle("Book " + random.nextInt(1000) + 1); //$NON-NLS-1$
                
                // an undoable change
                book.setAuthor(writer[0]);
                
                // not an undoable change
                resource.setURI(URI.createURI("http://localhost/" //$NON-NLS-1$
                        + random.nextInt(1000) + "/foo.xmi")); //$NON-NLS-1$
            }};
        
        // add trigger commands that create nested transactions during rollback
        ResourceSetListener pl = new TriggerListener(NotificationFilter.NOT_TOUCH) {
            @Override
            protected Command trigger(TransactionalEditingDomain domain,
                    Notification notification) {
                
                if (enable[0]) {
                    enable[0] = false;
                    return new AbstractCommand("Test") { //$NON-NLS-1$
                    
                        @Override
                        protected boolean prepare() {
                            return true;
                        }
                        
                        void undoRedo() {
                            InternalTransactionalEditingDomain idomain =
                                (InternalTransactionalEditingDomain) ValidationRollbackTest.this.domain;
                            try {
                                Transaction xa = idomain.startTransaction(false, idomain.getUndoRedoOptions());
                                run.run();
                                xa.commit();
                            } catch (Exception e) {
                                fail(e);
                            }
                        }
                        
                        @Override
                        public void undo() {
                            undoRedo();
                        }
                        
                        public void redo() {
                            undoRedo();
                        }
                    
                        public void execute() {
                            run.run();
                        }
                    };
                }
                return null;
            }};
        domain.addResourceSetListener(pl);
        
        // another listener to force rollback on commit of root transaction
        ResourceSetListener pl2 = new ResourceSetListenerImpl(NotificationFilter.NOT_TOUCH) {
            @Override
            public boolean isPrecommitOnly() {
                return true;
            }
            @Override
            public boolean isAggregatePrecommitListener() {
                return true;
            }
            @Override
            public Command transactionAboutToCommit(ResourceSetChangeEvent event)
                    throws RollbackException {
                
                throw new RollbackException(new Status(IStatus.ERROR,
                        "org.eclipse.emf.transaction.tests", //$NON-NLS-1$
                        "Please, roll back.")); //$NON-NLS-1$
            }};
        domain.addResourceSetListener(pl2);
        
        try {
            // find the writer to use
            startReading();
            writer[0] = (Writer) find("root/level1/level2/Level2 Writer"); //$NON-NLS-1$
            assertNotNull(writer[0]);
            commit();
            
            Transaction xa = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
            
            // in the outer transaction, make some change
            run.run();
            
            // start an inner read/write transaction
            Transaction inner = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
            
            run.run();
            
            inner.commit();

            // a change between nested transactions
            run.run();
            
            // another nested transaction
            inner = ((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
            run.run();
            
            // before committing, enable the trigger listener
            enable[0] = true;
            inner.commit();
            
            xa.commit();
            
            fail("Commit of root transaction should have failed."); //$NON-NLS-1$
        } catch (RollbackException e) {
            // roll-back is expected
        } catch (Exception e) {
            fail(e);
        } finally {
            domain.removeResourceSetListener(pl2);
            domain.removeResourceSetListener(pl);
            domain.removeResourceSetListener(tl);
        }
    }
    
    /**
     * Tests that, when a command execution is canceled with the
     * {@link AbortExecutionException}, the transaction is committed because it
     * didn't make any changes, but the command is not on the undo stack.
     */
    public void test_rollbackOnAbortExecutionException_230129() {
        Command command = new RecordingCommand(domain) {
            @Override
			protected void doExecute() {
                throw new AbortExecutionException("Cancel me"); //$NON-NLS-1$
            }};
        
        try {
            getCommandStack().execute(command, Collections.EMPTY_MAP);
            
            assertFalse(getCommandStack().canUndo());
        } catch (RollbackException e) {
            fail("Should not have rolled back: " + e.getLocalizedMessage()); //$NON-NLS-1$
        } catch (InterruptedException e) {
            fail("Interrupted"); //$NON-NLS-1$
        }
    }
	
	//
	// Fixture methods
	//
	
	@Override
	protected void doSetUp()
		throws Exception {
		
		super.doSetUp();
		
		// enable validation
		validationEnabled = true;
	}
	
	@Override
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
