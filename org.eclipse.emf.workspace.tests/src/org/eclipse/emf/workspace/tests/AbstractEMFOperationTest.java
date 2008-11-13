/**
 * <copyright>
 *
 * Copyright (c) 2005, 2008 IBM Corporation, Zeligsoft Inc. and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bugs 218276, 245419, 245393, 250253
 *   IBM - Bug 245393
 *
 * </copyright>
 *
 * $Id: AbstractEMFOperationTest.java,v 1.12 2008/11/13 01:16:38 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests;

import java.util.Map;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.ResourceSetListenerImpl;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TransactionalEditingDomainEvent;
import org.eclipse.emf.transaction.TransactionalEditingDomainListenerImpl;
import org.eclipse.emf.transaction.TriggerListener;
import org.eclipse.emf.transaction.impl.InternalTransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.emf.workspace.AbstractEMFOperation;
import org.eclipse.emf.workspace.CompositeEMFOperation;
import org.eclipse.emf.workspace.tests.fixtures.ExternalDataCommand;
import org.eclipse.emf.workspace.tests.fixtures.ItemDefaultPublicationDateTrigger;
import org.eclipse.emf.workspace.tests.fixtures.LibraryDefaultBookTrigger;
import org.eclipse.emf.workspace.tests.fixtures.LibraryDefaultNameTrigger;
import org.eclipse.emf.workspace.tests.fixtures.TestListener;
import org.eclipse.emf.workspace.tests.fixtures.TestOperation;
import org.eclipse.emf.workspace.tests.fixtures.TestUndoContext;


/**
 * Tests the {@link AbstractEMFOperation} framework.
 *
 * @author Christian W. Damus (cdamus)
 */
@SuppressWarnings("nls")
public class AbstractEMFOperationTest extends AbstractTest {

	public AbstractEMFOperationTest(String name) {
		super(name);
	}
	
	public static Test suite() {
		return new TestSuite(AbstractEMFOperationTest.class, "EMF Operation Tests"); //$NON-NLS-1$
	}
	
	public void test_execute_undo_redo() {
        UndoRedoResourceSetListener listener = new UndoRedoResourceSetListener();
        domain.addResourceSetListener(listener);
        
        assertEquals(0, listener.undoCount);
        
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		final String oldTitle = book.getTitle();
		final Writer oldAuthor = book.getAuthor();
		
		final String newTitle = "New Title"; //$NON-NLS-1$
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		commit();
		
		IUndoContext ctx = new TestUndoContext();
		
		IUndoableOperation oper = new TestOperation(domain) {
			@Override
			protected void doExecute() {
				book.setTitle(newTitle);
				newAuthor.getBooks().add(book);
			}};
		
		try {
			oper.addContext(ctx);
			history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were applied
		assertSame(newTitle, book.getTitle());
		assertSame(newAuthor, book.getAuthor());
		
		commit();

		try {
			assertTrue(history.canUndo(ctx));
			history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
        assertEquals(1, listener.undoCount);
        
		startReading();
		
		// verify that the changes were undone
		assertSame(oldTitle, book.getTitle());
		assertSame(oldAuthor, book.getAuthor());
		
		commit();
		
		try {
			assertTrue(history.canRedo(ctx));
			history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
        assertEquals(2, listener.undoCount);
        
		startReading();
		
		// verify that the changes were redone
		assertSame(newTitle, book.getTitle());
		assertSame(newAuthor, book.getAuthor());
		
		commit();
	}
	
	/**
	 * Tests that trigger commands are executed correctly when executing operations,
	 * including undo and redo, where those triggers do non-EMF work.
	 */
	public void test_triggerCommands_nonEMF() {
		final String[] externalData = new String[] {"..."}; //$NON-NLS-1$
		
		// one trigger sets the external data
		domain.addResourceSetListener(new TriggerListener() {
		
			@Override
			protected Command trigger(TransactionalEditingDomain domain,
					Notification notification) {
				if (notification.getFeature() == EXTLibraryPackage.Literals.LIBRARY__NAME) {
					return new ExternalDataCommand(externalData, notification.getNewStringValue());
				}
				
				return null;
			}});
		
		final Library[] newLibrary = new Library[1];
		
		IUndoContext ctx = new TestUndoContext();
		
		IUndoableOperation oper = new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library.  Our triggers will set a default name and book
				newLibrary[0] = EXTLibraryFactory.eINSTANCE.createLibrary();
				root.getBranches().add(newLibrary[0]);
				
				assertNull(newLibrary[0].getName());
				assertTrue(newLibrary[0].getBranches().isEmpty());
				
				newLibrary[0].setName("New Library"); //$NON-NLS-1$
			}};
		
		try {
			oper.addContext(ctx);
			history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		assertEquals("New Library", newLibrary[0].getName()); //$NON-NLS-1$
		assertEquals("New Library", externalData[0]); //$NON-NLS-1$
		
		commit();

		try {
			assertTrue(history.canUndo(ctx));
			history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were undone
		assertFalse(root.getBranches().contains(newLibrary[0]));
		assertEquals("...", externalData[0]); //$NON-NLS-1$
		
		commit();
		
		try {
			assertTrue(history.canRedo(ctx));
			history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were redone
		assertTrue(root.getBranches().contains(newLibrary[0]));
		assertEquals("New Library", newLibrary[0].getName()); //$NON-NLS-1$
		assertEquals("New Library", externalData[0]); //$NON-NLS-1$
		
		commit();
	}
	
	/**
	 * Tests that trigger commands are executed correctly when executing operations,
	 * including undo and redo.
	 */
	public void test_triggerCommands() {
		// one trigger sets default library names
		domain.addResourceSetListener(new LibraryDefaultNameTrigger());
		
		// another (distinct) trigger creates default books in new libraries
		domain.addResourceSetListener(new LibraryDefaultBookTrigger());
		
		final Library[] newLibrary = new Library[1];
		
		IUndoContext ctx = new TestUndoContext();
		
		IUndoableOperation oper = new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library.  Our triggers will set a default name and book
				newLibrary[0] = EXTLibraryFactory.eINSTANCE.createLibrary();
				root.getBranches().add(newLibrary[0]);
				
				assertNull(newLibrary[0].getName());
				assertTrue(newLibrary[0].getBranches().isEmpty());
			}};
		
		try {
			oper.addContext(ctx);
			history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		assertEquals("New Library", newLibrary[0].getName()); //$NON-NLS-1$
		assertEquals(1, newLibrary[0].getBooks().size());
		assertEquals("New Book", newLibrary[0].getBooks().get(0).getTitle()); //$NON-NLS-1$
		
		commit();

		try {
			assertTrue(history.canUndo(ctx));
			history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were undone
		assertFalse(root.getBranches().contains(newLibrary[0]));
		
		commit();
		
		try {
			assertTrue(history.canRedo(ctx));
			history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were redone
		assertTrue(root.getBranches().contains(newLibrary[0]));
		assertEquals("New Library", newLibrary[0].getName()); //$NON-NLS-1$
		assertEquals(1, newLibrary[0].getBooks().size());
		assertEquals("New Book", newLibrary[0].getBooks().get(0).getTitle()); //$NON-NLS-1$
		
		commit();
	}
	
	/**
	 * Tests that a command resulting from a pre-commit (trigger) listener will,
	 * itself, trigger further changes.
	 */
	public void test_triggerCommands_cascading() {
		// add the trigger to create a default book in a new library
		domain.addResourceSetListener(new LibraryDefaultBookTrigger());
		
		// add another trigger that will set default publication dates for new items
		domain.addResourceSetListener(new ItemDefaultPublicationDateTrigger());
		
		final Library[] newLibrary = new Library[1];
		
		IUndoContext ctx = new TestUndoContext();
		
		IUndoableOperation oper = new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library.  Our triggers will set a default name and book
				newLibrary[0] = EXTLibraryFactory.eINSTANCE.createLibrary();
				root.getBranches().add(newLibrary[0]);
				
				assertNull(newLibrary[0].getName());
				assertTrue(newLibrary[0].getBranches().isEmpty());
			}};
		
		try {
			oper.addContext(ctx);
			history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// the book is created by the first trigger
		assertEquals(1, newLibrary[0].getBooks().size());
		Book book = newLibrary[0].getBooks().get(0);
		assertEquals("New Book", book.getTitle()); //$NON-NLS-1$
		
		// the publication date is created by the cascaded trigger
		assertNotNull(book.getPublicationDate());
		
		commit();

		try {
			assertTrue(history.canUndo(ctx));
			history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were undone
		assertFalse(root.getBranches().contains(newLibrary[0]));
		
		commit();
		
		try {
			assertTrue(history.canRedo(ctx));
			history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were redone
		assertTrue(root.getBranches().contains(newLibrary[0]));
		assertEquals(1, newLibrary[0].getBooks().size());
		book = newLibrary[0].getBooks().get(0);
		assertEquals("New Book", book.getTitle()); //$NON-NLS-1$
		assertNotNull(book.getPublicationDate());
		
		commit();
	}
	
	/**
	 * Tests that validation correctly rolls back changes and fails execution.
	 */
	public void test_validation() {
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		final String oldTitle = book.getTitle();
		final Writer oldAuthor = book.getAuthor();
		
		final String newTitle = null; // will fail validation
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		commit();
		
		IUndoContext ctx = new TestUndoContext();
		
		IUndoableOperation oper = new TestOperation(domain) {
			@Override
			protected void doExecute() {
				book.setTitle(newTitle);
				newAuthor.getBooks().add(book);
			}};
		
		IStatus status = null;
			
		try {
			validationEnabled = true;
			oper.addContext(ctx);
			status = history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		} finally {
			validationEnabled = false;
		}
		
		assertNotNull(status);
		assertTrue(status.matches(IStatus.ERROR));
		
		status = findValidationStatus(status, IStatus.ERROR);
		assertNotNull(status);
		
		startReading();
		
		// verify that the changes were rolled back
		assertSame(oldTitle, book.getTitle());
		assertSame(oldAuthor, book.getAuthor());
		
		commit();
	}
	
	/**
	 * Tests the application of options to the transaction used for executing.
	 */
	public void test_options_124741() {
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		final String newTitle = null; // would cause validation failure
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		commit();
		
		TestListener listener = new TestListener();
		domain.addResourceSetListener(listener);
		
		IUndoContext ctx = new TestUndoContext();
		
		IUndoableOperation oper = new TestOperation(
				domain,
				makeOptions(
					Transaction.OPTION_NO_NOTIFICATIONS, true,
					Transaction.OPTION_NO_TRIGGERS, true,
					Transaction.OPTION_NO_VALIDATION, true)) {
			
			@Override
			protected void doExecute() {
				book.setTitle(newTitle);
				newAuthor.getBooks().add(book);
			}};
		
		IStatus status = null;
		try {
			validationEnabled = true;
			oper.addContext(ctx);
			status = history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		} finally {
			validationEnabled = false;
		}
		
		startReading();
		
		// verify that the changes were applied.  This asserts that execution did
		//    succeed
		assertSame(newTitle, book.getTitle());
		assertSame(newAuthor, book.getAuthor());
		
		commit();

		// no validation was performed
		assertNotNull(status);
		assertTrue(status.isOK());
		
		// no triggers were invoked
		assertNull(listener.precommit);
		
		// no listeners were notified
		assertNull(listener.postcommit);
	}
	
	/**
	 * Tests that, when an exception unwinds the Java stack during the execution
	 * of an AbstractEMFOperation, the active transactions are rolled back in
	 * the correct sequence.
	 */
	public void test_rollbackNestingTransactionOnException_135673() {
		CompositeEMFOperation outer = new CompositeEMFOperation(domain, ""); //$NON-NLS-1$
		AbstractEMFOperation inner = new AbstractEMFOperation(domain, "") { //$NON-NLS-1$
			@Override
			public boolean canExecute() {
				return true;
			}
			@Override
			protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
					throws ExecutionException {
				// start some nested transactions
				try {
					((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
					((InternalTransactionalEditingDomain) domain).startTransaction(false, null);
				} catch (Exception e) {
					fail("Failed to start nested transaction: " + e.getLocalizedMessage()); //$NON-NLS-1$
				}
				throw new TestError("intentional error"); //$NON-NLS-1$
			}};
			
		outer.add(inner);
		
		try {
			outer.execute(new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail("Unexpected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (TestError error) {
			// success case -- error was not masked by IllegalStateException
		} catch (IllegalArgumentException e) {
			fail("Rolled back out of order: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}
	}
	
	public void test_undoRedoNonEMFOperationWithEMFChanges_155268() {
		final CompositeEMFOperation comp = new CompositeEMFOperation(domain, ""); //$NON-NLS-1$
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		
		final AbstractEMFOperation emfOperation = new AbstractEMFOperation(domain, "") { //$NON-NLS-1$
			@Override
			public boolean canExecute() {
				return true;
			}
			
			@Override
			protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
				throws ExecutionException {
				
				book.setTitle("155268"); //$NON-NLS-1$
				
				return Status.OK_STATUS;
			}
		};
		
		IUndoableOperation nonEMFOperation = new IUndoableOperation() {
			private IUndoableOperation wrappedOperation = emfOperation;

			public void addContext(IUndoContext context) {
				wrappedOperation.addContext(context);
			}

			public boolean canExecute() {
				return wrappedOperation.canExecute();
			}

			public boolean canRedo() {
				return wrappedOperation.canRedo();
			}

			public boolean canUndo() {
				return wrappedOperation.canUndo();
			}

			public void dispose() {
				wrappedOperation.dispose();
			}

			public IStatus execute(IProgressMonitor monitor, IAdaptable info)
				throws ExecutionException {
				return wrappedOperation.execute(monitor, info);
			}

			public IUndoContext[] getContexts() {
				return wrappedOperation.getContexts();
			}

			public String getLabel() {
				return wrappedOperation.getLabel();
			}

			public boolean hasContext(IUndoContext context) {
				return wrappedOperation.hasContext(context);
			}

			public IStatus redo(IProgressMonitor monitor, IAdaptable info)
				throws ExecutionException {
				return wrappedOperation.redo(monitor, info);
			}

			public void removeContext(IUndoContext context) {
				wrappedOperation.removeContext(context);
			}

			public IStatus undo(IProgressMonitor monitor, IAdaptable info)
				throws ExecutionException {
				return wrappedOperation.undo(monitor, info);
			}
		};
		
		AbstractEMFOperation root = new AbstractEMFOperation(domain, "") { //$NON-NLS-1$
			@Override
			public boolean canExecute() {
				return true;
			}
			
			@Override
			protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
				throws ExecutionException {
				
				comp.execute(monitor, info);
				
				return Status.OK_STATUS;
			}
		};
		
		comp.add(nonEMFOperation);
		
		try {
			root.execute(new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail("Unexpected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (TestError error) {
			// success case -- error was not masked by IllegalStateException
		} catch (IllegalArgumentException e) {
			fail("Rolled back out of order: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}
		
		assertEquals(book.getTitle(),"155268"); //$NON-NLS-1$
		
		try {
			root.undo(new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail("Unexpected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (TestError error) {
			// success case -- error was not masked by IllegalStateException
		} catch (IllegalArgumentException e) {
			fail("Rolled back out of order: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}
		
		assertFalse("155268".equals(book.getTitle())); //$NON-NLS-1$
		
		try {
			root.redo(new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail("Unexpected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		} catch (TestError error) {
			// success case -- error was not masked by IllegalStateException
		} catch (IllegalArgumentException e) {
			fail("Rolled back out of order: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}
		
		assertEquals(book.getTitle(),"155268"); //$NON-NLS-1$
	}
    
    /**
     * Tests that execute/undo/redo are contented with <code>null</code> as the
     * progress monitor.
     */
    public void test_nullProgressMonitors_bug150033() {
        IUndoableOperation operation = new AbstractEMFOperation(domain, "Test") { //$NON-NLS-1$
        
            @Override
			protected IStatus doUndo(IProgressMonitor monitor, IAdaptable info)
                throws ExecutionException {
                
                monitor.isCanceled();
                
                return super.doUndo(monitor, info);
            }
        
            @Override
			protected IStatus doRedo(IProgressMonitor monitor, IAdaptable info)
                throws ExecutionException {
                
                monitor.isCanceled();
                
                return super.doRedo(monitor, info);
            }
        
            @Override
			protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
                throws ExecutionException {
                
                monitor.isCanceled();
                return Status.OK_STATUS;
            }};
            
       try {
           operation.execute(null, null);
       } catch (Exception e) {
           fail("Should not have failed to execute with null monitor: " + e.getLocalizedMessage()); //$NON-NLS-1$
       }
       
       try {
           operation.undo(null, null);
       } catch (Exception e) {
           fail("Should not have failed to undo with null monitor: " + e.getLocalizedMessage()); //$NON-NLS-1$
       }
           
       try {
           operation.redo(null, null);
       } catch (Exception e) {
           fail("Should not have failed to redo with null monitor: " + e.getLocalizedMessage()); //$NON-NLS-1$
       }
    }
    
    /**
     * Tests that recording-commands used as triggers are not undone twice.
     */
    public void test_undoWithRecordingCommandTrigger_218276() {
    	final Book[] book = new Book[] {(Book) find("root/Root Book")}; //$NON-NLS-1$
    	final int newCopies = 30;
    	
    	final RecordingCommand trigger = new RecordingCommand(domain, "Test Trigger") { //$NON-NLS-1$
		
			@Override
			protected void doExecute() {
				book[0].setCopies(newCopies);
			}};
    	
		ResourceSetListener listener = new ResourceSetListenerImpl() {
			@Override
			public boolean isPrecommitOnly() {
				return true;
			}
			
			@Override
			public Command transactionAboutToCommit(ResourceSetChangeEvent event)
					throws RollbackException {
				
				CompoundCommand result = new CompoundCommand();
				
				for (Notification next : event.getNotifications()) {
					if (next.getFeature() == EXTLibraryPackage.Literals.BOOK__TITLE) {
						return trigger;
					}
				}
				
				return result;
			}};
		
		try {
			domain.addResourceSetListener(listener);
			
			final String newTitle = "New Title"; //$NON-NLS-1$
			
			IUndoableOperation op = new TestOperation(domain) {
				@Override
				protected void doExecute()
						throws ExecutionException {
					book[0].setTitle(newTitle);
				}};

			op.execute(null, null);
			
			assertEquals("Wrong number of copies on execute", newCopies, book[0].getCopies()); //$NON-NLS-1$
			
			op.undo(null, null);
			
			assertFalse("Wrong number of copies on undo", book[0].getCopies() == newCopies); //$NON-NLS-1$
			
			op.redo(null, null);
			
			assertEquals("Wrong number of copies on redo", newCopies, book[0].getCopies()); //$NON-NLS-1$
		} catch (ExecutionException e) {
			fail(e);
		} finally {
			domain.removeResourceSetListener(listener);
		}
    }
    
    /**
     * Tests the API for changing options after construction of the operation.
     */
    public void test_setOptions_245419() {
		startReading();
		
		final Book book = (Book) find("root/Root Book");
		assertNotNull(book);
		
		final String newTitle = "New Title";
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer");
		assertNotNull(newAuthor);
		
		commit();
		
		Map<Object, Object> options = new java.util.HashMap<Object, Object>();
		options.put("one", 1);
		options.put("two", 2);
		
		IUndoContext ctx = new TestUndoContext();
		
		TestOperation oper = new TestOperation(domain, options) {
			@Override
			protected void doExecute() {
				book.setTitle(newTitle);
				newAuthor.getBooks().add(book);
			}};
		
		// this should be no surprise
		assertSubset(options, oper.getOptions());
		
		// the options were copied from the original map
		options.clear();
		options.put("first", true);
		options.put("second", false);
		assertNotSubset(options, oper.getOptions());
		
		// set the new options
		assertTrue(oper.canSetOptions());
		oper.setOptions(options);
		
		// they are, indeed, changed
		assertSubset(options, oper.getOptions());
		
		try {
			oper.addContext(ctx);
			history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// cannot change, now
		assertFalse(oper.canSetOptions());
		try {
			oper.setOptions(options);
			fail("Should not have been able to set options");
		} catch (IllegalStateException e) {
			// success
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		}
		
		try {
			assertTrue(history.canUndo(ctx));
			history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// nor now
		assertFalse(oper.canSetOptions());
		try {
			oper.setOptions(options);
			fail("Should not have been able to set options");
		} catch (IllegalStateException e) {
			// success
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		}
		
		try {
			assertTrue(history.canRedo(ctx));
			history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// and certainly not after a redo
		assertFalse(oper.canSetOptions());
		try {
			oper.setOptions(options);
			fail("Should not have been able to set options");
		} catch (IllegalStateException e) {
			// success
			System.out.println("Got expected exception: " + e.getLocalizedMessage());
		}
    }
    
    /**
	 * Tests that execution of an AbstractEMFOperation can be made to reuse the
	 * active transaction.
	 */
	public void test_executeInActiveTransaction_245393() {
		IUndoableOperation operation = new AbstractEMFOperation(domain,
			"Test_executeInActiveTransaction") { //$NON-NLS-1$

			@Override
			protected IStatus doExecute(IProgressMonitor monitor,
					IAdaptable info)
					throws ExecutionException {

				final Transaction outer = ((InternalTransactionalEditingDomain) getEditingDomain())
					.getActiveTransaction();
				AbstractEMFOperation delegate = new AbstractEMFOperation(
					domain, "Test_executeInActiveTransaction_delegate") { //$NON-NLS-1$

					@Override
					protected IStatus doExecute(IProgressMonitor monitor,
							IAdaptable info)
							throws ExecutionException {

						assertSame("Transaction not reused by inner operation",
							outer,
							((InternalTransactionalEditingDomain) getEditingDomain())
								.getActiveTransaction());
						
						return Status.OK_STATUS;
					}
				};

				// re-use the outer operation's transaction for the inner
				delegate.setReuseParentTransaction(true);

				return delegate.execute(monitor, info);
			}
		};

		try {
			operation.execute(new NullProgressMonitor(), null);
		} catch (Exception e) {
			fail("Unexpected exception: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}
	}
	
	public void test_rollbackOnError_250253() {
		RollbackListener l = new RollbackListener();
		l.install(domain);

		try {
			new AbstractEMFOperation(domain, "Test error result") {

				@Override
				protected IStatus doExecute(IProgressMonitor monitor,
						IAdaptable info)
						throws ExecutionException {

					return new Status(IStatus.ERROR,
						"org.eclipse.emf.transaction.tests", "I want to fail");
				}
			}.execute(null, null);
			
			l.assertRolledBack();
		} catch (ExecutionException e) {
			fail("Shouldn't have an execution exception from a normal error return."
				+ e.getLocalizedMessage());
		} finally {
			l.uninstall(domain);
		}
	}
	
	//
	// Fixtures
	//
	
    void assertSubset(Map<?, ?> expected, Map<?, ?> actual) {
		for (Map.Entry<?, ?> next : expected.entrySet()) {
			assertEquals("map is not a subset", next.getValue(), actual
				.get(next.getKey()));
		}
	}
	
    void assertNotSubset(Map<?, ?> notExpected, Map<?, ?> actual) {
    	boolean subset = true;
    	
		for (Map.Entry<?, ?> next : notExpected.entrySet()) {
			Object value = actual.get(next.getKey());
			
			if (value == null) {
				if (next.getValue() != null) {
					subset = false;
					break;
				}
			} else if (!value.equals(next.getValue())) {
				subset = false;
				break;
			}
		}
		
		assertFalse("map is a subset", subset);
	}
    
	static class TestError extends Error {
		private static final long serialVersionUID = 1502966836790504386L;

		TestError(String msg) {
			super(msg);
		}
	}
    
    public class UndoRedoResourceSetListener implements ResourceSetListener {
        public int undoCount = 0;
        
        public NotificationFilter getFilter() {
            return null;
        }

        public boolean isAggregatePrecommitListener() {
            return false;
        }

        public boolean isPostcommitOnly() {
            return false;
        }

        public boolean isPrecommitOnly() {
            return false;
        }

        public void resourceSetChanged(ResourceSetChangeEvent event) {
            Transaction transaction = event.getTransaction();
            
            Object obj = transaction.getOptions().get(Transaction.OPTION_IS_UNDO_REDO_TRANSACTION);
            if (Boolean.TRUE.equals(obj)) {
                undoCount++;
            }
        }

        public Command transactionAboutToCommit(ResourceSetChangeEvent event)
                throws RollbackException {
            return null;
        }
    }
    
	public static class RollbackListener
			extends TransactionalEditingDomainListenerImpl {

		private int rollbackCount = 0;

		public void install(TransactionalEditingDomain domain) {
			TransactionUtil.getAdapter(domain,
				TransactionalEditingDomain.Lifecycle.class)
				.addTransactionalEditingDomainListener(this);
		}

		public void uninstall(TransactionalEditingDomain domain) {
			TransactionUtil.getAdapter(domain,
				TransactionalEditingDomain.Lifecycle.class)
				.removeTransactionalEditingDomainListener(this);
		}
		
		public void reset() {
			rollbackCount = 0;
		}

		@Override
		public void transactionClosed(TransactionalEditingDomainEvent event) {
			if (event.getTransaction().getStatus().getSeverity() >= IStatus.ERROR) {
				rollbackCount++;
			}
		}

		public void assertRolledBack() {
			assertEquals("No rollback occurred", 1, rollbackCount);
		}

		public void assertRollbacks(int expected) {
			assertEquals("Wrong number of rollbacks", expected, rollbackCount);
		}

		public void assertNoRollbacks() {
			assertEquals("Should not have any rollbacks", 0, rollbackCount);
		}
	}
}
