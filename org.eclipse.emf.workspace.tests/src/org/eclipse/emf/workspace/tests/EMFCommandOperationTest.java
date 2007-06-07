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
 * $Id: EMFCommandOperationTest.java,v 1.3 2007/06/07 14:26:03 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TriggerListener;
import org.eclipse.emf.workspace.EMFCommandOperation;
import org.eclipse.emf.workspace.tests.fixtures.ItemDefaultPublicationDateTrigger;
import org.eclipse.emf.workspace.tests.fixtures.LibraryDefaultBookTrigger;
import org.eclipse.emf.workspace.tests.fixtures.LibraryDefaultNameTrigger;
import org.eclipse.emf.workspace.tests.fixtures.TestCommand;
import org.eclipse.emf.workspace.tests.fixtures.TestUndoContext;


/**
 * Tests the {@link EMFCommandOperation} class.
 *
 * @author Christian W. Damus (cdamus)
 */
public class EMFCommandOperationTest extends AbstractTest {

	public EMFCommandOperationTest(String name) {
		super(name);
	}
	
	public static Test suite() {
		return new TestSuite(EMFCommandOperationTest.class, "EMF Command Operation Tests"); //$NON-NLS-1$
	}
	
	public void test_execute_undo_redo() {
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
		
		Command cmd = new SetCommand(
				domain,
				book,
				EXTLibraryPackage.eINSTANCE.getBook_Title(),
				newTitle);
		cmd = cmd.chain(new AddCommand(
				domain,
				newAuthor,
				EXTLibraryPackage.eINSTANCE.getWriter_Books(),
				book));
		IUndoableOperation oper = new EMFCommandOperation(domain, cmd);
		
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
		
		startReading();
		
		// verify that the changes were redone
		assertSame(newTitle, book.getTitle());
		assertSame(newAuthor, book.getAuthor());
		
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
		
		final Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		
		IUndoContext ctx = new TestUndoContext();
		
		// add a new library.  Our triggers will set a default name and book
		Command cmd = new AddCommand(
				domain,
				root,
				EXTLibraryPackage.eINSTANCE.getLibrary_Branches(),
				newLibrary);
		IUndoableOperation oper = new EMFCommandOperation(domain, cmd);
		
		try {
			oper.addContext(ctx);
			history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		assertEquals("New Library", newLibrary.getName()); //$NON-NLS-1$
		assertEquals(1, newLibrary.getBooks().size());
		assertEquals("New Book", ((Book) newLibrary.getBooks().get(0)).getTitle()); //$NON-NLS-1$
		
		commit();

		try {
			assertTrue(history.canUndo(ctx));
			history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were undone
		assertFalse(root.getBranches().contains(newLibrary));
		
		commit();
		
		try {
			assertTrue(history.canRedo(ctx));
			history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were redone
		assertTrue(root.getBranches().contains(newLibrary));
		assertEquals("New Library", newLibrary.getName()); //$NON-NLS-1$
		assertEquals(1, newLibrary.getBooks().size());
		assertEquals("New Book", ((Book) newLibrary.getBooks().get(0)).getTitle()); //$NON-NLS-1$
		
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
		
		final Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		
		IUndoContext ctx = new TestUndoContext();
		
		// add a new library.  Our triggers will set a default name and book
		Command cmd = new AddCommand(
				domain,
				root,
				EXTLibraryPackage.eINSTANCE.getLibrary_Branches(),
				newLibrary);
		IUndoableOperation oper = new EMFCommandOperation(domain, cmd);
		
		try {
			oper.addContext(ctx);
			history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// the book is created by the first trigger
		assertEquals(1, newLibrary.getBooks().size());
		Book book = (Book) newLibrary.getBooks().get(0);
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
		assertFalse(root.getBranches().contains(newLibrary));
		
		commit();
		
		try {
			assertTrue(history.canRedo(ctx));
			history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were redone
		assertTrue(root.getBranches().contains(newLibrary));
		assertEquals(1, newLibrary.getBooks().size());
		book = (Book) newLibrary.getBooks().get(0);
		assertEquals("New Book", book.getTitle()); //$NON-NLS-1$
		assertNotNull(book.getPublicationDate());
		
		commit();
	}
	
	/**
	 * Tests that an EMF Command Operation works well with recording commands.
	 */
	public void test_RecordingCommand_execute_undo_redo() {
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
		
		Command cmd = new RecordingCommand(domain) {
			protected void doExecute() {
				book.setTitle(newTitle);
				newAuthor.getBooks().add(book);
			}};
			
		IUndoableOperation oper = new EMFCommandOperation(domain, cmd);
		
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
		
		startReading();
		
		// verify that the changes were redone
		assertSame(newTitle, book.getTitle());
		assertSame(newAuthor, book.getAuthor());
		
		commit();
	}
	
	/**
	 * Tests that trigger commands on recording commands are correctly undone, by
	 * the recording command, itself (which records the entire transaction).
	 */
	public void test_RecordingCommand_triggerCommands() {
		// one trigger sets default library names
		domain.addResourceSetListener(new LibraryDefaultNameTrigger());
		
		// another (distinct) trigger creates default books in new libraries
		domain.addResourceSetListener(new LibraryDefaultBookTrigger());
		
		final Library newLibrary = EXTLibraryFactory.eINSTANCE.createLibrary();
		
		IUndoContext ctx = new TestUndoContext();
		
		// add a new library.  Our triggers will set a default name and book
		Command cmd = new RecordingCommand(domain) {
			protected void doExecute() {
				root.getBranches().add(newLibrary);
			}};
		
		IUndoableOperation oper = new EMFCommandOperation(domain, cmd);
		
		try {
			oper.addContext(ctx);
			history.execute(oper, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		assertEquals("New Library", newLibrary.getName()); //$NON-NLS-1$
		assertEquals(1, newLibrary.getBooks().size());
		assertEquals("New Book", ((Book) newLibrary.getBooks().get(0)).getTitle()); //$NON-NLS-1$
		
		commit();

		try {
			assertTrue(history.canUndo(ctx));
			history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were undone
		assertFalse(root.getBranches().contains(newLibrary));
		
		commit();
		
		try {
			assertTrue(history.canRedo(ctx));
			history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were redone
		assertTrue(root.getBranches().contains(newLibrary));
		assertEquals("New Library", newLibrary.getName()); //$NON-NLS-1$
		assertEquals(1, newLibrary.getBooks().size());
		assertEquals("New Book", ((Book) newLibrary.getBooks().get(0)).getTitle()); //$NON-NLS-1$
		
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
		
		Command cmd = new SetCommand(
				domain,
				book,
				EXTLibraryPackage.eINSTANCE.getBook_Title(),
				newTitle);
		cmd = cmd.chain(new AddCommand(
				domain,
				newAuthor,
				EXTLibraryPackage.eINSTANCE.getWriter_Books(),
				book));
		IUndoableOperation oper = new EMFCommandOperation(domain, cmd);
		
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
	 * Tests that the the <code>EMFCommandOperation</code> tests its wrapped
	 * command for redoability.
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
	 * Tests that the <code>EMFCommandOperation</code> tests its wrapped trigger
	 * command for redoability.
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
		
		// this command *is* implicitly redoable; it is the trigger that is not
		Command cmd = AddCommand.create(
				domain, root, EXTLibraryPackage.Literals.LIBRARY__BRANCHES,
				newLibrary);
		
		getCommandStack().execute(cmd);
		
		assertTrue(getCommandStack().canUndo());
		
		getCommandStack().undo();
		
		assertFalse(getCommandStack().canRedo());
	}
}
