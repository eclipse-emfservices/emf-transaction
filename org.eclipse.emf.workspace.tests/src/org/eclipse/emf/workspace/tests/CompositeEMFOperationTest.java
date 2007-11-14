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
 * $Id: CompositeEMFOperationTest.java,v 1.5 2007/11/14 18:13:53 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ListIterator;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.commands.operations.ICompositeOperation;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.UndoContext;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.examples.extlibrary.Book;
import org.eclipse.emf.examples.extlibrary.EXTLibraryFactory;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.examples.extlibrary.Writer;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListenerImpl;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.util.CompositeChangeDescription;
import org.eclipse.emf.workspace.CompositeEMFOperation;
import org.eclipse.emf.workspace.tests.fixtures.LibraryDefaultBookTrigger;
import org.eclipse.emf.workspace.tests.fixtures.LibraryDefaultNameTrigger;
import org.eclipse.emf.workspace.tests.fixtures.NonEMFCompositeOperation;
import org.eclipse.emf.workspace.tests.fixtures.NullOperation;
import org.eclipse.emf.workspace.tests.fixtures.TestOperation;
import org.eclipse.emf.workspace.tests.fixtures.TestUndoContext;


/**
 * Tests the {@link CompositeEMFOperation} class.
 *
 * @author Christian W. Damus (cdamus)
 */
public class CompositeEMFOperationTest extends AbstractTest {
	private static IStatus ERROR_STATUS =
		new Status(IStatus.ERROR, "bogus", 1, "no message", null); //$NON-NLS-1$ //$NON-NLS-2$
	
	public CompositeEMFOperationTest(String name) {
		super(name);
	}
	
	public static Test suite() {
		return new TestSuite(CompositeEMFOperationTest.class, "Composite EMF Operation Tests"); //$NON-NLS-1$
	}
	
	/**
	 * Tests that the undo contexts of the composite correctly aggregate the
	 * contexts of the children that it contains.
	 */
	public void test_contexts() {
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		IUndoContext ctx1 = new UndoContext();
		IUndoContext ctx2 = new UndoContext();
		IUndoContext ctx3 = new UndoContext();
		
		IUndoableOperation child1 = new NullOperation();
		IUndoableOperation child2 = new NullOperation();
		IUndoableOperation child3 = new NullOperation();
		
		// configure some contexts
		child1.addContext(ctx1);
		child2.addContext(ctx2);
		child2.addContext(ctx1);
		child3.addContext(ctx3);
		
		// no contexts, yet
		assertEquals(
				Collections.EMPTY_LIST,
				Arrays.asList(composite.getContexts()));
		
		composite.add(child1);
		assertEquals(
				Arrays.asList(new IUndoContext[] {ctx1}),
				Arrays.asList(composite.getContexts()));
		
		// note that we don't get ctx1 twice
		composite.add(child2);
		assertEquals(
				Arrays.asList(new IUndoContext[] {ctx1, ctx2}),
				Arrays.asList(composite.getContexts()));
		
		composite.add(child3);
		assertEquals(
				Arrays.asList(new IUndoContext[] {ctx1, ctx2, ctx3}),
				Arrays.asList(composite.getContexts()));
		
		// still have ctx1, but not ctx2
		composite.remove(child2);
		assertEquals(
				Arrays.asList(new IUndoContext[] {ctx1, ctx3}),
				Arrays.asList(composite.getContexts()));
		
		composite.remove(child1);
		assertEquals(
				Arrays.asList(new IUndoContext[] {ctx3}),
				Arrays.asList(composite.getContexts()));
		
		composite.remove(child3);
		assertEquals(
				Collections.EMPTY_LIST,
				Arrays.asList(composite.getContexts()));
	}
	
	/**
	 * Tests that the undo contexts of the composite correctly aggregate the
	 * contexts of the children that it contains, when manipulating the children
	 * using a list iterator.
	 */
	public void test_contexts_listIterator_125151() {
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		IUndoContext ctx1 = new UndoContext();
		IUndoContext ctx2 = new UndoContext();
		IUndoContext ctx3 = new UndoContext();
		
		IUndoableOperation child1 = new NullOperation();
		IUndoableOperation child2 = new NullOperation();
		IUndoableOperation child3 = new NullOperation();
		
		// configure some contexts
		child1.addContext(ctx1);
		child2.addContext(ctx2);
		child2.addContext(ctx1);
		child3.addContext(ctx3);
		
		ListIterator<IUndoableOperation> iter = composite.listIterator();
		
		// no contexts, yet
		assertEquals(
				Collections.EMPTY_LIST,
				Arrays.asList(composite.getContexts()));
		
		iter.add(child1);
		assertEquals(
				Arrays.asList(new IUndoContext[] {ctx1}),
				Arrays.asList(composite.getContexts()));
		
		// note that we don't get ctx1 twice
		iter.add(child2);
		assertEquals(
				Arrays.asList(new IUndoContext[] {ctx1, ctx2}),
				Arrays.asList(composite.getContexts()));
		
		iter.add(child3);
		assertEquals(
				Arrays.asList(new IUndoContext[] {ctx1, ctx2, ctx3}),
				Arrays.asList(composite.getContexts()));
		
		// still have ctx1, but not ctx2 when we remove child2
		iter.previous();
		iter.previous();
		iter.remove();
		assertEquals(
				Arrays.asList(new IUndoContext[] {ctx1, ctx3}),
				Arrays.asList(composite.getContexts()));
		
		// removing child1
		iter.previous();
		iter.remove();
		assertEquals(
				Arrays.asList(new IUndoContext[] {ctx3}),
				Arrays.asList(composite.getContexts()));
		
		// removing child3
		iter.next();
		iter.remove();
		assertEquals(
				Collections.EMPTY_LIST,
				Arrays.asList(composite.getContexts()));
	}
	
	/**
	 * Tests the aggregation of canExecute() from child operations.
	 */
	public void test_canExecute() {
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		CompositeEMFOperation composite2 = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		composite.add(new NullOperation());
		composite.add(new NullOperation());
		composite.add(composite2);
		composite.add(new NullOperation());
		
		composite2.add(new NullOperation());
		composite2.add(new NullOperation(false)); // can't execute this one
		composite2.add(new NullOperation());
		
		assertFalse(composite.canExecute());
	}
	
	/**
	 * Tests the aggregation of canUndo() from child operations.
	 */
	public void test_canUndo() {
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		CompositeEMFOperation composite2 = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		composite.add(new NullOperation());
		composite.add(new NullOperation());
		composite.add(composite2);
		composite.add(new NullOperation());
		
		composite2.add(new NullOperation());
		composite2.add(new NullOperation(true, false)); // can't undo this one
		composite2.add(new NullOperation());
		
		composite.addContext(ctx);
		assertTrue(composite.canExecute());
		
		try {
			history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		assertFalse(composite.canUndo());
		assertFalse(history.canUndo(ctx));
	}
	
	/**
	 * Tests the aggregation of canRedo() from child operations.
	 */
	public void test_canRedo() {
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		CompositeEMFOperation composite2 = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		composite.add(new NullOperation());
		composite.add(new NullOperation());
		composite.add(composite2);
		composite.add(new NullOperation());
		
		composite2.add(new NullOperation());
		composite2.add(new NullOperation(true, true, false)); // can undo but not redo
		composite2.add(new NullOperation());
		
		composite.addContext(ctx);
		assertTrue(composite.canExecute());
		
		try {
			history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		assertTrue(composite.canUndo());
		assertTrue(history.canUndo(ctx));
		
		try {
			history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		assertFalse(composite.canRedo());
		assertFalse(history.canRedo(ctx));
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
		
		// non-EMF "external" data to be modified by the composite
		final String[] externalData = new String[1];
		externalData[0] = "..."; //$NON-NLS-1$
		
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		composite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				book.setTitle(newTitle);
			}});
		
		composite.add(new ChangeExternalData(externalData, book));
		
		composite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				newAuthor.getBooks().add(book);
			}});
		
		try {
			composite.addContext(ctx);
			history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were applied
		assertSame(newTitle, book.getTitle());
		assertEquals(newTitle, externalData[0]);
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
		assertEquals("...", externalData[0]); //$NON-NLS-1$
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
		assertEquals(newTitle, externalData[0]);
		assertSame(newAuthor, book.getAuthor());
		
		commit();
	}
	
	/**
	 * Tests undo/redo of nested non-EMF operations, to check the correct
	 * nesting of non-EMF transactions with change descriptions encapsulating
	 * non-EMF changes.
	 */
	public void test_execute_undo_redo_nested() {
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		final String oldTitle = book.getTitle();
		final Writer oldAuthor = book.getAuthor();
		
		final String newTitle = "New Title"; //$NON-NLS-1$
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		commit();
		
		// non-EMF "external" data to be modified by the composite
		final String[] externalData = new String[1];
		externalData[0] = "..."; //$NON-NLS-1$
		
		// non-EMF "external" data to be modified by the nested composite
		final String[] externalData2 = new String[1];
		externalData2[0] = ":::"; //$NON-NLS-1$
		
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		composite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				book.setTitle(newTitle);
			}});
		
		composite.add(new ChangeExternalData(externalData, book));
		
		// more nesting
		ICompositeOperation composite2 = new NonEMFCompositeOperation();
		composite2.add(new ChangeExternalData(externalData2, book));
		composite.add(composite2);
		
		// EMF change in the nested non-transactional composite
		composite2.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				newAuthor.getBooks().add(book);
			}});
		
		try {
			composite.addContext(ctx);
			history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		// verify that the changes were applied
		assertSame(newTitle, book.getTitle());
		assertEquals(newTitle, externalData[0]);
		assertEquals(newTitle, externalData2[0]);
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
		assertEquals("...", externalData[0]); //$NON-NLS-1$
		assertEquals(":::", externalData2[0]); //$NON-NLS-1$
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
		assertEquals(newTitle, externalData[0]);
		assertEquals(newTitle, externalData2[0]);
		assertSame(newAuthor, book.getAuthor());
		
		commit();
	}
	
	/**
	 * Tests rollback of nested non-EMF operations on validation failure, to
	 * check the correct nesting of non-EMF transactions with change
	 * descriptions encapsulating non-EMF changes.
	 */
	public void test_rollback_nested() {
		startReading();
		
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		assertNotNull(book);
		final String oldTitle = book.getTitle();
		final Writer oldAuthor = book.getAuthor();
		
		final Writer newAuthor = (Writer) find("root/level1/Level1 Writer"); //$NON-NLS-1$
		assertNotNull(newAuthor);
		
		commit();
		
		// non-EMF "external" data to be modified by the composite
		final String[] externalData = new String[1];
		externalData[0] = "..."; //$NON-NLS-1$
		
		// non-EMF "external" data to be modified by the nested composite
		final String[] externalData2 = new String[1];
		externalData2[0] = ":::"; //$NON-NLS-1$
		
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		composite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				newAuthor.getBooks().add(book);
			}});
		
		composite.add(new ChangeExternalData(externalData, book));
		
		// more nesting
		ICompositeOperation composite2 = new NonEMFCompositeOperation();
		composite2.add(new ChangeExternalData(externalData2, book));
		composite.add(composite2);
		
		// EMF change in the nested non-transactional composite
		composite2.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				book.setTitle(null);  // will not pass validation
			}});
		
		IStatus status = null;
		
		try {
			validationEnabled = true;
			composite.addContext(ctx);
			status = history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		} finally {
			validationEnabled = false;
		}
		
		// check that validation failed with an error
		assertNotNull(status);
		assertNotNull(findValidationStatus(status, IStatus.ERROR));
		
		startReading();
		
		// verify that the changes were rolled back
		assertSame(oldAuthor, book.getAuthor());
		assertEquals(oldTitle, book.getTitle());
		assertEquals("...", externalData[0]); //$NON-NLS-1$
		assertEquals(":::", externalData2[0]); //$NON-NLS-1$
		
		commit();
	}
	
	/**
	 * Tests that trigger commands are executed correctly when executing composite
	 * operations, including undo and redo.
	 */
	public void test_triggerCommands() {
		// one trigger sets default library names
		domain.addResourceSetListener(new LibraryDefaultNameTrigger());
		
		// another (distinct) trigger creates default books in new libraries
		domain.addResourceSetListener(new LibraryDefaultBookTrigger());
		
		final Library[] newLibrary = new Library[1];
		
		// non-EMF "external" data to be modified by the composite
		final String[] externalData = new String[1];
		externalData[0] = "..."; //$NON-NLS-1$
		
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		composite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library.  Our triggers will set a default name and book
				newLibrary[0] = EXTLibraryFactory.eINSTANCE.createLibrary();
				root.getBranches().add(newLibrary[0]);
				
				assertNull(newLibrary[0].getName());
				assertTrue(newLibrary[0].getBooks().isEmpty());
			}});
		
		// the operation will find the new book and set the externalData from its
		//   title.  Note that this depends on triggers of the previous operation!
		composite.add(new ChangeExternalData(externalData, newLibrary));
		
		try {
			composite.addContext(ctx);
			history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		startReading();
		
		assertEquals("New Library", newLibrary[0].getName()); //$NON-NLS-1$
		assertEquals(1, newLibrary[0].getBooks().size());
		assertEquals("New Book", newLibrary[0].getBooks().get(0).getTitle()); //$NON-NLS-1$
		assertEquals("New Book", externalData[0]); //$NON-NLS-1$
		
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
		assertFalse("New Library".equals(newLibrary[0].getName())); //$NON-NLS-1$
		assertEquals(0, newLibrary[0].getBooks().size());
		
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
		assertEquals("New Book", externalData[0]); //$NON-NLS-1$
		
		commit();
	}
	
	/**
	 * Tests that commands from aggregate pre-commit listeners are executed
	 * correctly when executing composite operations, including undo and redo.
	 * This is different from the non-aggregate case because they are
	 * contributed only to the top-level transaction (which otherwise has no
	 * changes of its own).
	 */
	public void test_triggerCommands_aggregate() {
		// one trigger sets default library names
		domain.addResourceSetListener(new LibraryDefaultNameTrigger(true));
		
		// another (distinct) trigger creates default books in new libraries
		domain.addResourceSetListener(new LibraryDefaultBookTrigger(true));
		
		final Library[] newLibrary = new Library[1];
		
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		composite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library.  Our triggers will set a default name and book
				newLibrary[0] = EXTLibraryFactory.eINSTANCE.createLibrary();
				root.getBranches().add(newLibrary[0]);
				
				assertNull(newLibrary[0].getName());
				assertTrue(newLibrary[0].getBooks().isEmpty());
			}});
		
		try {
			composite.addContext(ctx);
			history.execute(composite, new NullProgressMonitor(), null);
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
		assertFalse("New Library".equals(newLibrary[0].getName())); //$NON-NLS-1$
		assertEquals(0, newLibrary[0].getBooks().size());
		
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
		
		// non-EMF "external" data to be modified by the composite
		final String[] externalData = new String[1];
		externalData[0] = "..."; //$NON-NLS-1$
		
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		composite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				book.setTitle(newTitle);
			}});
		
		composite.add(new ChangeExternalData(externalData, book));
		
		composite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				newAuthor.getBooks().add(book);
			}});
		
		IStatus status = null;
		
		try {
			validationEnabled = true;
			composite.addContext(ctx);
			status = history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			// success
			fail(e);
		} finally {
			validationEnabled = false;
		}
		
		assertNotNull(status);
		assertTrue(status.matches(IStatus.ERROR));
		
		status = findValidationStatus(status, IStatus.ERROR);
		assertNotNull(status);
		
		startReading();
		
		// verify that the changes were rolled back, *including* the non-EMF change
		assertSame(oldTitle, book.getTitle());
		assertEquals("...", externalData[0]); //$NON-NLS-1$
		assertSame(oldAuthor, book.getAuthor());
		
		commit();
	}
	
	/**
	 * Tests error detection during execution.
	 */
	public void test_execute_error_123614() {
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		MarkerOperation marker1 = new MarkerOperation();
		composite.add(marker1);
		
		FailCancelOperation op = new FailCancelOperation(
			ERROR_STATUS, Status.OK_STATUS, Status.OK_STATUS, false);
		composite.add(op);
		
		MarkerOperation marker2 = new MarkerOperation();
		composite.add(marker2);
		
		IStatus status = null;
		
		try {
			composite.addContext(ctx);
			status = history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were executed and which rolled back
		assertTrue(marker1.wasExecuted);
		assertFalse(marker2.wasExecuted);
		assertTrue(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.ERROR, status.getSeverity());
	}
	
	/**
	 * Tests cancel-status detection during execution.
	 */
	public void test_execute_cancel_123614() {
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		MarkerOperation marker1 = new MarkerOperation();
		composite.add(marker1);
		
		FailCancelOperation op = new FailCancelOperation(
			Status.CANCEL_STATUS, Status.OK_STATUS, Status.OK_STATUS, false);
		composite.add(op);
		
		MarkerOperation marker2 = new MarkerOperation();
		composite.add(marker2);
		
		IStatus status = null;
		
		try {
			composite.addContext(ctx);
			status = history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were executed and which rolled back
		assertTrue(marker1.wasExecuted);
		assertFalse(marker2.wasExecuted);
		assertTrue(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.CANCEL, status.getSeverity());
	}
	
	/**
	 * Tests monitor-cancel detection during execution.
	 */
	public void test_execute_cancelMonitor_123614() {
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		MarkerOperation marker1 = new MarkerOperation();
		composite.add(marker1);
		
		FailCancelOperation op = new FailCancelOperation(
			Status.CANCEL_STATUS, Status.OK_STATUS, Status.OK_STATUS, true);
		composite.add(op);
		
		MarkerOperation marker2 = new MarkerOperation();
		composite.add(marker2);
		
		IStatus status = null;
		
		try {
			composite.addContext(ctx);
			status = history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were executed and which rolled back
		assertTrue(marker1.wasExecuted);
		assertFalse(marker2.wasExecuted);
		assertTrue(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.CANCEL, status.getSeverity());
	}
	
	/**
	 * Tests error detection during undo.
	 */
	public void test_undo_error_123614() {
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		MarkerOperation marker1 = new MarkerOperation();
		composite.add(marker1);
		
		FailCancelOperation op = new FailCancelOperation(
			Status.OK_STATUS, ERROR_STATUS, Status.OK_STATUS, false);
		composite.add(op);
		
		MarkerOperation marker2 = new MarkerOperation();
		composite.add(marker2);
		
		IStatus status = null;
		
		try {
			composite.addContext(ctx);
			status = history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were executed and which rolled back
		assertTrue(marker1.wasExecuted);
		assertTrue(marker2.wasExecuted);
		assertFalse(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.OK, status.getSeverity());
		
		marker1.reset();
		marker2.reset();
		
		try {
			status = history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were undone and which were redone
		assertFalse(marker1.wasUndone);
		assertTrue(marker2.wasUndone);
		assertTrue(marker2.wasRedone);
		
		// check overall operation status
		assertEquals(IStatus.ERROR, status.getSeverity());
	}
	
	/**
	 * Tests cancel-status detection during undo.
	 */
	public void test_undo_cancel_123614() {
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		MarkerOperation marker1 = new MarkerOperation();
		composite.add(marker1);
		
		FailCancelOperation op = new FailCancelOperation(
			Status.OK_STATUS, Status.CANCEL_STATUS, Status.OK_STATUS, false);
		composite.add(op);
		
		MarkerOperation marker2 = new MarkerOperation();
		composite.add(marker2);
		
		IStatus status = null;
		
		try {
			composite.addContext(ctx);
			status = history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were executed and which rolled back
		assertTrue(marker1.wasExecuted);
		assertTrue(marker2.wasExecuted);
		assertFalse(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.OK, status.getSeverity());
		
		marker1.reset();
		marker2.reset();
		
		try {
			status = history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were undone and which were redone
		assertFalse(marker1.wasUndone);
		assertTrue(marker2.wasUndone);
		assertTrue(marker2.wasRedone);
		
		// check overall operation status
		assertEquals(IStatus.CANCEL, status.getSeverity());
	}
	
	/**
	 * Tests monitor-cancel detection during undo.
	 */
	public void test_undo_cancelMonitor_123614() {
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		MarkerOperation marker1 = new MarkerOperation();
		composite.add(marker1);
		
		FailCancelOperation op = new FailCancelOperation(
			Status.OK_STATUS, Status.CANCEL_STATUS, Status.OK_STATUS, true);
		composite.add(op);
		
		MarkerOperation marker2 = new MarkerOperation();
		composite.add(marker2);
		
		IStatus status = null;
		
		try {
			composite.addContext(ctx);
			status = history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were executed and which rolled back
		assertTrue(marker1.wasExecuted);
		assertTrue(marker2.wasExecuted);
		assertFalse(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.OK, status.getSeverity());
		
		marker1.reset();
		marker2.reset();
		
		try {
			status = history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were undone and which were redone
		assertFalse(marker1.wasUndone);
		assertTrue(marker2.wasUndone);
		assertTrue(marker2.wasRedone);
		
		// check overall operation status
		assertEquals(IStatus.CANCEL, status.getSeverity());
	}
	
	/**
	 * Tests error detection during redo.
	 */
	public void test_redo_error_123614() {
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		MarkerOperation marker1 = new MarkerOperation();
		composite.add(marker1);
		
		FailCancelOperation op = new FailCancelOperation(
			Status.OK_STATUS, Status.OK_STATUS, ERROR_STATUS, false);
		composite.add(op);
		
		MarkerOperation marker2 = new MarkerOperation();
		composite.add(marker2);
		
		IStatus status = null;
		
		try {
			composite.addContext(ctx);
			status = history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were executed and which rolled back
		assertTrue(marker1.wasExecuted);
		assertTrue(marker2.wasExecuted);
		assertFalse(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.OK, status.getSeverity());
		
		marker1.reset();
		marker2.reset();
		
		try {
			status = history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were undone and which were redone
		assertTrue(marker1.wasUndone);
		assertTrue(marker2.wasUndone);
		assertFalse(marker2.wasRedone);
		
		// check overall operation status
		assertEquals(IStatus.OK, status.getSeverity());
		
		marker1.reset();
		marker2.reset();
		
		try {
			status = history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were undone and which were redone
		assertTrue(marker1.wasRedone);
		assertFalse(marker2.wasRedone);
		assertTrue(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.ERROR, status.getSeverity());
	}
	
	/**
	 * Tests cancel-status detection during redo.
	 */
	public void test_redo_cancel_123614() {
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		MarkerOperation marker1 = new MarkerOperation();
		composite.add(marker1);
		
		FailCancelOperation op = new FailCancelOperation(
			Status.OK_STATUS, Status.OK_STATUS, Status.CANCEL_STATUS, false);
		composite.add(op);
		
		MarkerOperation marker2 = new MarkerOperation();
		composite.add(marker2);
		
		IStatus status = null;
		
		try {
			composite.addContext(ctx);
			status = history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were executed and which rolled back
		assertTrue(marker1.wasExecuted);
		assertTrue(marker2.wasExecuted);
		assertFalse(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.OK, status.getSeverity());
		
		marker1.reset();
		marker2.reset();
		
		try {
			status = history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were undone and which were redone
		assertTrue(marker1.wasUndone);
		assertTrue(marker2.wasUndone);
		assertFalse(marker2.wasRedone);
		
		// check overall operation status
		assertEquals(IStatus.OK, status.getSeverity());
		
		marker1.reset();
		marker2.reset();
		
		try {
			status = history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were undone and which were redone
		assertTrue(marker1.wasRedone);
		assertFalse(marker2.wasRedone);
		assertTrue(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.CANCEL, status.getSeverity());
	}
	
	/**
	 * Tests monitor-cancel detection during redo.
	 */
	public void test_redo_cancelMonitor_123614() {
		IUndoContext ctx = new TestUndoContext();
		
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		MarkerOperation marker1 = new MarkerOperation();
		composite.add(marker1);
		
		FailCancelOperation op = new FailCancelOperation(
			Status.OK_STATUS, Status.OK_STATUS, Status.CANCEL_STATUS, true);
		composite.add(op);
		
		MarkerOperation marker2 = new MarkerOperation();
		composite.add(marker2);
		
		IStatus status = null;
		
		try {
			composite.addContext(ctx);
			status = history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were executed and which rolled back
		assertTrue(marker1.wasExecuted);
		assertTrue(marker2.wasExecuted);
		assertFalse(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.OK, status.getSeverity());
		
		marker1.reset();
		marker2.reset();
		
		try {
			status = history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were undone and which were redone
		assertTrue(marker1.wasUndone);
		assertTrue(marker2.wasUndone);
		assertFalse(marker2.wasRedone);
		
		// check overall operation status
		assertEquals(IStatus.OK, status.getSeverity());
		
		marker1.reset();
		marker2.reset();
		
		try {
			status = history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		// check which markers were undone and which were redone
		assertTrue(marker1.wasRedone);
		assertFalse(marker2.wasRedone);
		assertTrue(marker1.wasUndone);
		
		// check overall operation status
		assertEquals(IStatus.CANCEL, status.getSeverity());
	}
	
	/**
	 * Tests that an pure EMF composite operation can use a single, unnested,
	 * transaction.
	 */
	public void test_noTransactionNesting_pureEMF_135545() {
		TransactionCapture capture = new TransactionCapture();
		domain.addResourceSetListener(capture);
		
		startReading();
		
		int originalBranchCount = root.getBranches().size();
		
		commit();

		IUndoContext ctx = new TestUndoContext();
		
		// create a nested composite operation structure
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		// tell the composite to reuse a single transaction
		composite.setTransactionNestingEnabled(false);
		
		composite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library
				root.getBranches().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			}});

		CompositeEMFOperation nestedComposite = new CompositeEMFOperation(domain, "Nested"); //$NON-NLS-1$
		nestedComposite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library
				root.getBranches().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			}});
		nestedComposite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library
				root.getBranches().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			}});

		composite.add(nestedComposite);
		
		try {
			composite.addContext(ctx);
			history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		Transaction transaction = capture.getTransaction();
		assertNotNull(transaction);
		
		// only one transaction (contributing one change description to the composite)
		Collection<ChangeDescription> changes = getChanges(transaction);
		assertEquals(1, changes.size());
		
		capture.clear();
		
		startReading();
		
		assertEquals(originalBranchCount + 3, root.getBranches().size());
		
		commit();

		try {
			assertTrue(history.canUndo(ctx));
			history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		transaction = capture.getTransaction();
		assertNotNull(transaction);
		
		// transaction didn't record any changes on undo
		changes = getChanges(transaction);
		assertEquals(0, changes.size());
		
		startReading();
		
		// verify that the changes were undone
		assertEquals(originalBranchCount, root.getBranches().size());
		
		commit();
		
		try {
			assertTrue(history.canRedo(ctx));
			history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		transaction = capture.getTransaction();
		assertNotNull(transaction);
		
		// transaction didn't record any changes on redo
		changes = getChanges(transaction);
		assertEquals(0, changes.size());
		
		startReading();
		
		// verify that the changes were redone
		assertEquals(originalBranchCount + 3, root.getBranches().size());
		
		commit();
	}
	
	/**
	 * Tests that a mixed EMF and non-EMF composite operation can use a single,
	 * unnested, transaction as much as possible (some nesting must still occur
	 * to account for non-EMF changes).
	 */
	public void test_noTransactionNesting_mixed_135545() {
		TransactionCapture capture = new TransactionCapture();
		domain.addResourceSetListener(capture);
		
		startReading();
		
		int originalBranchCount = root.getBranches().size();
		final Book book = (Book) find("root/Root Book"); //$NON-NLS-1$
		final String title = book.getTitle();
		
		commit();

		// non-EMF "external" data to be modified by the composite
		final String[] externalData = new String[1];
		externalData[0] = "..."; //$NON-NLS-1$
		
		IUndoContext ctx = new TestUndoContext();
		
		// create a nested composite operation structure
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		// tell the composite to reuse a single transaction
		composite.setTransactionNestingEnabled(false);
		
		composite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library
				root.getBranches().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			}});

		CompositeEMFOperation nestedComposite = new CompositeEMFOperation(domain, "Nested"); //$NON-NLS-1$
		nestedComposite.add(new ChangeExternalData(externalData, book));  // non-EMF change
		nestedComposite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library
				root.getBranches().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			}});
		nestedComposite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library
				root.getBranches().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			}});

		composite.add(nestedComposite);
		
		try {
			composite.addContext(ctx);
			history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		Transaction transaction = capture.getTransaction();
		assertNotNull(transaction);
		
		// only two transactions:  the parent and a single child for the
		//    non-EMF changes.  These result in three changes:  one change
		//    before the non-EMF changes, one change for the non-EMF changes,
		//    and one change for everything after.  We would have 4 changes
		//    without the no-nesting hint
		Collection<ChangeDescription> changes = getChanges(transaction);
		assertEquals(3, changes.size());
		
		capture.clear();
		
		startReading();
		
		// verify that the changes were done
		assertEquals(originalBranchCount + 3, root.getBranches().size());
		assertEquals(title, externalData[0]);
		
		commit();

		try {
			assertTrue(history.canUndo(ctx));
			history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		transaction = capture.getTransaction();
		assertNotNull(transaction);
		
		// transaction didn't record any changes on undo
		changes = getChanges(transaction);
		assertEquals(0, changes.size());
		
		startReading();
		
		// verify that the changes were undone
		assertEquals(originalBranchCount, root.getBranches().size());
		assertEquals("...", externalData[0]); //$NON-NLS-1$
		
		commit();
		
		try {
			assertTrue(history.canRedo(ctx));
			history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		transaction = capture.getTransaction();
		assertNotNull(transaction);
		
		// transaction didn't record any changes on redo
		changes = getChanges(transaction);
		assertEquals(0, changes.size());
		
		startReading();
		
		// verify that the changes were redone
		assertEquals(originalBranchCount + 3, root.getBranches().size());
		assertEquals(title, externalData[0]);
		
		commit();
	}
	
	/**
	 * Tests that an EMF operation with different options forces nesting.
	 */
	public void test_noTransactionNesting_differentOptions_135545() {
		TransactionCapture capture = new TransactionCapture();
		domain.addResourceSetListener(capture);
		
		startReading();
		
		int originalBranchCount = root.getBranches().size();
		
		commit();

		IUndoContext ctx = new TestUndoContext();
		
		// create a nested composite operation structure
		CompositeEMFOperation composite = new CompositeEMFOperation(domain, "Composite"); //$NON-NLS-1$
		
		// tell the composite to reuse a single transaction
		composite.setTransactionNestingEnabled(false);
		
		composite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library
				root.getBranches().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			}});

		CompositeEMFOperation nestedComposite = new CompositeEMFOperation(domain, "Nested"); //$NON-NLS-1$
		nestedComposite.add(new TestOperation(domain,
				// this transaction has different options
				Collections.singletonMap(Transaction.OPTION_NO_NOTIFICATIONS, Boolean.TRUE)) {
			@Override
			protected void doExecute() {
				// add a new library
				root.getBranches().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			}});
		nestedComposite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library
				root.getBranches().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			}});
		nestedComposite.add(new TestOperation(domain) {
			@Override
			protected void doExecute() {
				// add a new library
				root.getBranches().add(EXTLibraryFactory.eINSTANCE.createLibrary());
			}});

		composite.add(nestedComposite);
		
		try {
			composite.addContext(ctx);
			history.execute(composite, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		Transaction transaction = capture.getTransaction();
		assertNotNull(transaction);
		
		// only two transactions:  the parent and a single child for the
		//    non-EMF changes.  These result in three changes:  one change
		//    before the different options, one change for the different options,
		//    and one change for everything after.  We would have 4 changes
		//    without the no-nesting hint
		Collection<ChangeDescription> changes = getChanges(transaction);
		assertEquals(3, changes.size());
		
		capture.clear();
		
		startReading();
		
		assertEquals(originalBranchCount + 4, root.getBranches().size());
		
		commit();

		try {
			assertTrue(history.canUndo(ctx));
			history.undo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		transaction = capture.getTransaction();
		assertNotNull(transaction);
		
		// transaction didn't record any changes on undo
		changes = getChanges(transaction);
		assertEquals(0, changes.size());
		
		startReading();
		
		// verify that the changes were undone
		assertEquals(originalBranchCount, root.getBranches().size());
		
		commit();
		
		try {
			assertTrue(history.canRedo(ctx));
			history.redo(ctx, new NullProgressMonitor(), null);
		} catch (ExecutionException e) {
			fail(e);
		}
		
		transaction = capture.getTransaction();
		assertNotNull(transaction);
		
		// transaction didn't record any changes on redo
		changes = getChanges(transaction);
		assertEquals(0, changes.size());
		
		startReading();
		
		// verify that the changes were redone
		assertEquals(originalBranchCount + 4, root.getBranches().size());
		
		commit();
	}
	
	//
	// Test fixtures
	//
	
	/**
	 * Does a reflective hack to get the private <tt>changes</tt> field of a
	 * composite change description.
	 */
	@SuppressWarnings("unchecked")
	private Collection<ChangeDescription> getChanges(Transaction tx) {
		Collection<ChangeDescription> result = null;
		CompositeChangeDescription composite = (CompositeChangeDescription) tx.getChangeDescription();
		
		try {
			Field changes = composite.getClass().getDeclaredField("changes"); //$NON-NLS-1$
			changes.setAccessible(true);
			result = (Collection<ChangeDescription>) changes.get(composite);
		} catch (Exception e) {
			fail("Could not access private changes field of CompositeChangeDescription: " + e.getLocalizedMessage()); //$NON-NLS-1$
		}
		return result;
	}
	
	private static class ChangeExternalData extends AbstractOperation {
		private final String[] externalData;
		private Book book;
		private Library[] library;
		private String oldData;
		
		ChangeExternalData(String[] externalData, Book book) {
			super("Change External Data"); //$NON-NLS-1$
			
			this.externalData = externalData;
			this.book = book;
		}
		
		ChangeExternalData(String[] externalData, Library[] library) {
			super("Change External Data"); //$NON-NLS-1$
			
			this.externalData = externalData;
			this.library = library;
		}
		
		@Override
		public IStatus execute(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
			oldData = externalData[0];
			
			// can safely read in the enclosing composite's write transaction
			if (book == null) {
				// get the book from the new library, then
				book = library[0].getBooks().get(0);
			}
			
			externalData[0] = book.getTitle();
			
			return Status.OK_STATUS;
		}
		
		@Override
		public IStatus undo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
			externalData[0] = oldData;
			return Status.OK_STATUS;
		}
		
		@Override
		public IStatus redo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
			externalData[0] = book.getTitle();
			return Status.OK_STATUS;
		}
	}
	
	private static class FailCancelOperation extends AbstractOperation {
		private final IStatus executeStatus;
		private final IStatus undoStatus;
		private final IStatus redoStatus;
		private final boolean cancelMonitor;
		
		FailCancelOperation(IStatus exec, IStatus undo, IStatus redo, boolean cancel) {
			super("Fail/Cancel Operation"); //$NON-NLS-1$
			this.executeStatus = exec;
			this.undoStatus = undo;
			this.redoStatus = redo;
			this.cancelMonitor = cancel;
		}
		
		@Override
		public IStatus execute(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
			if ((executeStatus.getSeverity() == IStatus.CANCEL) && cancelMonitor) {
				monitor.setCanceled(true);
				return Status.OK_STATUS;
			}
			
			return executeStatus;
		}
		
		@Override
		public IStatus undo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
			if ((undoStatus.getSeverity() == IStatus.CANCEL) && cancelMonitor) {
				monitor.setCanceled(true);
				return Status.OK_STATUS;
			}
			
			return undoStatus;
		}
		
		@Override
		public IStatus redo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
			if ((redoStatus.getSeverity() == IStatus.CANCEL) && cancelMonitor) {
				monitor.setCanceled(true);
				return Status.OK_STATUS;
			}
			
			return redoStatus;
		}
	}
	
	static class MarkerOperation extends AbstractOperation {
		boolean wasExecuted;
		boolean wasUndone;
		boolean wasRedone;
		
		MarkerOperation() {
			super("Marker operation"); //$NON-NLS-1$
		}
		
		void reset() {
			wasExecuted = false;
			wasUndone = false;
			wasRedone = false;
		}
		
		@Override
		public IStatus execute(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
			wasExecuted = true;
			return Status.OK_STATUS;
		}
		
		@Override
		public IStatus undo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
			wasUndone = true;
			return Status.OK_STATUS;
		}
		
		@Override
		public IStatus redo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
			wasRedone = true;
			return Status.OK_STATUS;
		}
	}
	
	private static class TransactionCapture extends ResourceSetListenerImpl {
		private Transaction transaction;
		
		@Override
		public boolean isPostcommitOnly() {
			return true;
		}
		
		@Override
		public void resourceSetChanged(ResourceSetChangeEvent event) {
			if (transaction == null) {
				transaction = event.getTransaction();
			}
		}
		
		void clear() {
			transaction = null;
		}
		
		Transaction getTransaction() {
			return transaction;
		}
	}
}
