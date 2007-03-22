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
 * $Id: UndoRedoTest.java,v 1.8 2007/03/22 17:27:10 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests;

import java.util.Iterator;
import java.util.Set;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CommandStack;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionChangeDescription;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TriggerListener;
import org.eclipse.emf.transaction.tests.fixtures.ItemDefaultPublicationDateTrigger;
import org.eclipse.emf.transaction.tests.fixtures.LibraryDefaultBookTrigger;
import org.eclipse.emf.transaction.tests.fixtures.TestCommand;
import org.eclipse.emf.transaction.util.ConditionalRedoCommand;


/**
 * Tests undo and redo of commands by the command stack.
 *
 * @author Christian W. Damus (cdamus)
 */
public class UndoRedoTest extends AbstractTest {
	
	public UndoRedoTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(UndoRedoTest.class, "Command Undo/Redo Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests the undo and redo support offered by the <code>RecordingCommand</code>
	 * class.
	 */
	public void test_recordingCommand() {
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
		
		Command cmd = new RecordingCommand(domain) {
			protected void doExecute() {
				book.setTitle(newTitle);
				newAuthor.getBooks().add(book);
			}};
		
		CommandStack stack = domain.getCommandStack();
		
		stack.execute(cmd);
		
		startReading();
		
		// verify that the changes were applied
		assertSame(newTitle, book.getTitle());
		assertSame(newAuthor, book.getAuthor());
		
		commit();
		
		assertTrue(stack.canUndo());
		stack.undo();
		
		assertEquals(1, listener.undoCount);
		
		startReading();
		
		// verify that the changes were undone
		assertSame(oldTitle, book.getTitle());
		assertSame(oldAuthor, book.getAuthor());
		
		commit();
		
		assertTrue(stack.canRedo());
		stack.redo();
		
		assertEquals(2, listener.undoCount);
		
		startReading();
		
		// verify that the changes were redone
		assertSame(newTitle, book.getTitle());
		assertSame(newAuthor, book.getAuthor());
		
		commit();

		domain.removeResourceSetListener(listener);
	}
	
	/**
	 * Tests that the changes made by trigger commands can be undone and redone,
	 * too, even when the original command is not a recording command.
	 */
	public void test_triggerCommands_nonRecording() {
		// add the trigger to create a default book in a new library
		domain.addResourceSetListener(new LibraryDefaultBookTrigger());
		
		// add another trigger that will set default publication dates for new items
		domain.addResourceSetListener(new ItemDefaultPublicationDateTrigger());
		
		final Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		
		Command cmd = new AddCommand(domain, root,
				EXTLibraryPackage.Literals.LIBRARY__BRANCHES, newLibrary);
		
		getCommandStack().execute(cmd);
		
		// check that the library exists and was correctly configured with a default book
		startReading();
		
		assertSame(root, newLibrary.eContainer());
		assertEquals(1, newLibrary.getBooks().size());
		Book book = (Book) newLibrary.getBooks().get(0);
		assertEquals("New Book", book.getTitle()); //$NON-NLS-1$
		assertNotNull(book.getPublicationDate());
		
		commit();
		
		getCommandStack().undo();
		
		// check that we undid OK
		startReading();
		
		// undoing attachment to the resource adds the library to the change description
		assertNotSame(root, newLibrary.eContainer());
		assertEquals(0, newLibrary.getBooks().size());
		
		commit();
		
		getCommandStack().redo();
		
		// check that we redid OK
		startReading();
		
		assertSame(root, newLibrary.eContainer());
		assertEquals(1, newLibrary.getBooks().size());
		book = (Book) newLibrary.getBooks().get(0);
		assertEquals("New Book", book.getTitle()); //$NON-NLS-1$
		assertNotNull(book.getPublicationDate());
		
		commit();
	}
	
	/**
	 * Tests that the changes made by trigger commands can be undone and redone
	 * when the original command is a recording command.
	 */
	public void test_triggerCommands_recording() {
		// add the trigger to create a default book in a new library
		domain.addResourceSetListener(new LibraryDefaultBookTrigger());
		
		// add another trigger that will set default publication dates for new items
		domain.addResourceSetListener(new ItemDefaultPublicationDateTrigger());
		
		final Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		
		Command cmd = new RecordingCommand(domain) {
			protected void doExecute() {
				// add a new library.  Our triggers will set a default name and book
				root.getBranches().add(newLibrary);
				
				assertNull(newLibrary.getName());
				assertTrue(newLibrary.getBranches().isEmpty());
			}};
		
		getCommandStack().execute(cmd);
		
		// check that the library exists and was correctly configured with a default book
		startReading();
		
		assertSame(root, newLibrary.eContainer());
		assertEquals(1, newLibrary.getBooks().size());
		Book book = (Book) newLibrary.getBooks().get(0);
		assertEquals("New Book", book.getTitle()); //$NON-NLS-1$
		assertNotNull(book.getPublicationDate());
		
		commit();
		
		getCommandStack().undo();
		
		// check that we undid OK
		startReading();
		
		// undoing attachment to the resource adds the library to the change description
		assertNotSame(root, newLibrary.eContainer());
		assertEquals(0, newLibrary.getBooks().size());
		
		commit();
		
		getCommandStack().redo();
		
		// check that we redid OK
		startReading();
		
		assertSame(root, newLibrary.eContainer());
		assertEquals(1, newLibrary.getBooks().size());
		book = (Book) newLibrary.getBooks().get(0);
		assertEquals("New Book", book.getTitle()); //$NON-NLS-1$
		assertNotNull(book.getPublicationDate());
		
		commit();
	}
	
	/**
	 * Tests that undoing the unload of a resource doesn't totally confuse the
	 * editing domain (which would mean that our strategy of using a change
	 * recorder for undo/redo is flawed).
	 */
	public void test_undoResourceUnload() {
		startReading();
		
		Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		Writer author = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(author);
		
		commit();
		
		startWriting();
		
		URI uri = unloadTestResource();
		
		Transaction tx = commit();
		TransactionChangeDescription change = tx.getChangeDescription();
		
		// check that the unload happened correctly
		assertTrue(book.eIsProxy());
		assertNull(book.eResource());
		assertTrue(author.eIsProxy());
		assertNull(author.eResource());
		
		assertTrue(change.canApply());
		
		startWriting(Transaction.OPTION_NO_UNDO);
		
		change.applyAndReverse();
		
		commit();
		
		startReading();
		
		// check that the unload was correctly undone
		assertSame(testResource, domain.getResourceSet().getResource(uri, false));
		assertNotNull(testResource);
		assertTrue(testResource.isLoaded());
//		assertFalse(book.eIsProxy());  // TODO: Should un-proxify on attachment
		assertSame(testResource, book.eResource());
//		assertFalse(author.eIsProxy());// TODO: Should un-proxify on attachment
		assertSame(testResource, author.eResource());
		
		commit();
		
		assertTrue(change.canApply());
		
		startWriting(Transaction.OPTION_NO_UNDO);
		
		change.applyAndReverse();
		
		commit();
		
		// check that the unload happened correctly (again)
		assertTrue(book.eIsProxy());
		assertNull(book.eResource());
		assertTrue(author.eIsProxy());
		assertNull(author.eResource());
	}
	
	/**
	 * Tests that undoing the load of a resource has no effect (as it does not
	 * alter that abstract state of the model/resource set).
	 */
	public void test_undoResourceLoad() {
		// set up initial conditions: resource is not loaded and does not exist
		startReading();
		
		URI uri = unloadTestResource();
		domain.getResourceSet().getResources().remove(testResource);
		
		commit();
		
		// begin test
		
		startWriting();
		
		testResource = domain.getResourceSet().getResource(uri, true);
		
		Transaction tx = commit();
		TransactionChangeDescription change = tx.getChangeDescription();
		
		startReading();
		
		// check that the load happened correctly
		assertNotNull(testResource);
		assertTrue(testResource.isLoaded());
		Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		Writer author = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(author);
		assertFalse(book.eIsProxy());  
		assertSame(testResource, book.eResource());
		assertFalse(author.eIsProxy());
		assertSame(testResource, author.eResource());
		
		commit();
		
		assertTrue(change.canApply());
		
		assertTrue(change.getObjectChanges().isEmpty());
		assertTrue(change.getResourceChanges().isEmpty());
		assertTrue(change.getObjectsToAttach().isEmpty());
		assertTrue(change.getObjectsToDetach().isEmpty());
	}
	
	/**
	 * Tests that trigger commands are not undone and redone multiple times
	 * when triggers are propagated to a parent transaction.
	 */
	public void test_triggerCommands_singleUndoRedo_133388() {
		class CountingCommand extends TestCommand {
			private int count;
			
			void reset() { count = 0; }
			
			public void execute() { reset(); }
			public void undo() { assertEquals(1, ++count); }
			public void redo() { assertEquals(1, ++count); }
		}
		
		final Set countingCommands = new java.util.HashSet();
		
		// add the trigger to create a default book in a new library, combined
		//    with a counting command
		domain.addResourceSetListener(new LibraryDefaultBookTrigger() {
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
				Command result = super.trigger(domain, notification);
				
				if (result != null) {
					CountingCommand cc = new CountingCommand();
					countingCommands.add(cc);
					result = result.chain(cc);
				}
				
				return result;
			}});
		
		// add another trigger that will set default publication dates for new
		//    items, combined with a counting command
		domain.addResourceSetListener(new ItemDefaultPublicationDateTrigger() {
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
				Command result = super.trigger(domain, notification);
				
				if (result != null) {
					CountingCommand cc = new CountingCommand();
					countingCommands.add(cc);
					result = result.chain(cc);
				}
				
				return result;
			}});
		startWriting();
		
		// add a new library.  Our triggers will set a default name and book
		Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		root.getBranches().add(newLibrary);
		
		assertNull(newLibrary.getName());
		assertTrue(newLibrary.getBranches().isEmpty());
		
		Transaction tx = commit();
		TransactionChangeDescription change = tx.getChangeDescription();
		
		startWriting();

		// undo.  Would fail assertion if a command was repeated
		change.applyAndReverse();
		
		commit();
		
		for (Iterator iter = countingCommands.iterator(); iter.hasNext();) {
			((CountingCommand) iter.next()).reset();
		}
		
		startWriting();

		// redo.  Would fail assertion if a command was repeated
		change.applyAndReverse();
		
		commit();
		
		for (Iterator iter = countingCommands.iterator(); iter.hasNext();) {
			((CountingCommand) iter.next()).reset();
		}
		
		startWriting();

		// un-redo.  Would fail assertion if a command was repeated
		change.applyAndReverse();
		
		commit();
		
		for (Iterator iter = countingCommands.iterator(); iter.hasNext();) {
			((CountingCommand) iter.next()).reset();
		}
	}
	
	/**
	 * Tests that the transactional command stack tests commands for redoability.
	 */
	public void test_nonredoableCommand_138287() {
		Command cmd = new TestCommand.Redoable() {
			public void execute() {
				// nothing to do
			}
		
			public boolean canRedo() {
				return false;
			}};
		
		getCommandStack().execute(cmd);
		
		assertTrue(getCommandStack().canUndo());
		
		getCommandStack().undo();
		
		assertFalse(getCommandStack().canRedo());
	}
	
	/**
	 * Tests that the transactional command stack tests trigger commands for
	 * redoability.
	 */
	public void test_nonredoableTriggerCommand_138287() {
		// add a trigger command that is not redoable
		domain.addResourceSetListener(new TriggerListener() {
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
				return new TestCommand.Redoable() {
					public void execute() {
						// nothing to do
					}
				
					public boolean canRedo() {
						return false;
					}};
			}});
		
		Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		
		ConditionalRedoCommand.Compound cmd = new ConditionalRedoCommand.Compound();
		
		// this command *is* implicitly redoable; it is the trigger that is not
		cmd.chain(AddCommand.create(
				domain, root, EXTLibraryPackage.Literals.LIBRARY__BRANCHES,
				newLibrary));
		
		newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		cmd.chain(AddCommand.create(
			domain, root, EXTLibraryPackage.Literals.LIBRARY__BRANCHES,
			newLibrary));

		getCommandStack().execute(cmd);
		
		assertTrue(getCommandStack().canUndo());
		
		getCommandStack().undo();
		
		assertFalse(getCommandStack().canRedo());
	}
	
	/**
	 * Tests that the transactional command stack tests trigger commands for
	 * redoability.
	 */
	public void test_nonredoableTriggerCommands() {
		// add a trigger command that is not redoable
		domain.addResourceSetListener(new TriggerListener() {
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
				return new TestCommand.Redoable() {
					public void execute() {
						// nothing to do
					}
				
					public boolean canRedo() {
						return false;
					}};
			}});
		
		// add a trigger command that is not redoable
		domain.addResourceSetListener(new TriggerListener() {
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
				return new TestCommand.Redoable() {
					public void execute() {
						// nothing to do
					}
				
					public boolean canRedo() {
						return false;
					}};
			}});
		
		Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		
		ConditionalRedoCommand.Compound cmd = new ConditionalRedoCommand.Compound();
		
		// this command *is* implicitly redoable; it is the trigger that is not
		cmd.chain(AddCommand.create(
				domain, root, EXTLibraryPackage.Literals.LIBRARY__BRANCHES,
				newLibrary));

		getCommandStack().execute(cmd);
		
		assertTrue(getCommandStack().canUndo());
		
		getCommandStack().undo();
		
		assertFalse(getCommandStack().canRedo());
	}
	
	/**
	 * Tests that the <code>CommandChangeDescription</code> checks its wrapped
	 * command for redoability in its <code>canApply()</code>, via a
	 * <code>RecordingCommand</code>'s redoability test.
	 */
	public void test_nonredoableTriggerCommand_RecordingCommand_138287() {
		// add a trigger command that is not redoable
		domain.addResourceSetListener(new TriggerListener() {
			protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
				return new TestCommand.Redoable() {
					public void execute() {
						// nothing to do
					}
				
					public boolean canRedo() {
						return false;
					}};
			}});
		
		final Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		
		// this command *is* implicitly redoable; it is the trigger that is not
		RecordingCommand cmd = new RecordingCommand(domain) {
			protected void doExecute() {
				// add a new library, just to record some change
				root.getBranches().add(newLibrary);
			}};
		
		
		getCommandStack().execute(cmd);
		
		assertTrue(getCommandStack().canUndo());
		
		getCommandStack().undo();
		
		assertFalse(getCommandStack().canRedo());
	}
	
	//
	// Fixture methods
	//
	
	protected void doSetUp()
		throws Exception {
		
		super.doSetUp();
		
		// enable validation
		ValidationRollbackTest.validationEnabled = true;
	}
	
	protected void doTearDown()
		throws Exception {
		
		// disable validation
		ValidationRollbackTest.validationEnabled = false;
		
		super.doTearDown();
	}
	
	private URI unloadTestResource() {
		URI result = testResource.getURI();
		
		testResource.unload();
		
		return result;
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
}
