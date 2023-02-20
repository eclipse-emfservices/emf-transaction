/**
 * Copyright (c) 2006, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.tests;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.util.ECrossReferenceAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.edit.command.CommandParameter;
import org.eclipse.emf.edit.command.RemoveCommand;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.ResourceSetListenerImpl;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TriggerListener;
import org.eclipse.emf.transaction.util.CompositeChangeDescription;


/**
 * Tests to check for memory leaks.
 *
 * @author Christian W. Damus (cdamus)
 */
public class MemoryLeakTest extends AbstractTest {
	public MemoryLeakTest(String name) {
		super(name);
	}

	public static Test suite() {
		return new TestSuite(MemoryLeakTest.class, "Memory Leak (GC) Tests"); //$NON-NLS-1$
	}

	/**
	 * Tests that unloading a resource allows its root(s) to be reclaimed.
	 */
	public void test_unloadResource() {
		ReferenceQueue<EObject> q = new ReferenceQueue<EObject>();
		Reference<EObject> ref = new WeakReference<EObject>(root, q);
		
		// make some change, causing a transaction to record a change description
		startWriting();
		root.setName("foo"); //$NON-NLS-1$
		commit();
		
		startReading();
		testResource.unload();
		commit();
		
		root = null; // forget our only other reference to this object

		System.gc();
		
		idle(2000);
		
		System.gc();
		
		assertSame(ref, q.poll());
	}

	/**
	 * Tests that an editing domain can be reclaimed if we forget all references
	 * to it and its resource set's contents.
	 */
	public void test_reclaimEditingDomain() {
		ReferenceQueue<TransactionalEditingDomain> q =
			new ReferenceQueue<TransactionalEditingDomain>();
		Reference<TransactionalEditingDomain> ref =
			new WeakReference<TransactionalEditingDomain>(domain, q);
		
		// make some change, causing a transaction to record a change description
		startWriting();
		root.setName("foo"); //$NON-NLS-1$
		commit();
		
		domain = null;        // forget the domain
		testResource = null;  // forget a resource in the domain
		root = null;          // forget an object in the domain
		
		System.gc();
		
		idle(2000);
		
		System.gc();
		
		assertSame(ref, q.poll());
	}
	
	/**
	 * Tests that a child transaction does not cause any interruption in the
	 * recording of the parent transaction's change description if the child is
	 * unrecorded.  The result is that we do not retain empty change
	 * descriptions and the parent has a single, seamless change description.
	 */
	public void test_nonRecordingChildTransactions_153908() {
		// report initial heap size
		long initialUsedHeap = usedHeap();
		
		startWriting();
		
		final String oldName = root.getName();
		 
		// make a change in the root-level transaction
		root.setName("foo"); //$NON-NLS-1$
		
		// now, create 1000 nested transactions, each doing a change, *but* unrecorded
		Map<Object, Object> options = Collections.<Object, Object>singletonMap(
			Transaction.OPTION_UNPROTECTED, Boolean.TRUE);
		
		for (int i = 0; i < 1000; i++) {
			startWriting(options);
			
			root.setName("foo" + i); //$NON-NLS-1$
			
			commit();
			
			// make another change in the root-level transaction
			root.setName("foo" + (i + 1000)); //$NON-NLS-1$
		}
		
		// report the used heap
		long currentUsedHeap = usedHeap();
		System.out.println("Additional heap used by the transaction: " //$NON-NLS-1$
				+ ((currentUsedHeap - initialUsedHeap) / 1024L) + " kB"); //$NON-NLS-1$
		
		Transaction tx = commit();
		CompositeChangeDescription change = (CompositeChangeDescription) tx.getChangeDescription();
		List<ChangeDescription> children = getChildren(change);
		
		assertEquals(1, children.size());
		
		// let's just make sure that undo works as expected
		startWriting(options);
		
		final String newName = root.getName();
		
		change.applyAndReverse();
		commit();
		assertEquals(oldName, root.getName());
		
		// and redo, too
		startWriting(options);
		change.applyAndReverse();
		commit();
		assertEquals(newName, root.getName());
	}
    
    /**
     * <p>
     * Tests that the change descriptions that recorded execution, undo, and
     * redo of the removal of an element that has an ECrossReferenceAdapter
     * attached do not leak that adapter after the command stack has been
     * flushed.
     * </p><p>
     * This is a control test, using a normal EMF <code>RemoveCommand</code>
     * that has been instrumented to clear the adapters of the removed
     * element(s) upon disposal.
     * </p>
     */
    public void test_crossReferenceAdapter_undoredo_normalCommands() {
        // attach a cross-reference adapter to the resource set
        ECrossReferenceAdapter xrefAdapter = new ECrossReferenceAdapter();
        domain.getResourceSet().eAdapters().add(xrefAdapter);
        
        // and a transaction-sniffer to the domain
        TransactionSniffer sniffer = new TransactionSniffer(domain);
        
        EObject level1 = find(root, "level1"); //$NON-NLS-1$
        
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        Command cmd = new RemoveCommand(domain, root, EXTLibraryPackage.Literals.LIBRARY__BRANCHES, level1) {
            @Override
			public void doDispose() {
                if (feature instanceof EReference && ((EReference) feature).isContainment()) {
                    for (Object o : collection) {
                        EObject next = (EObject) o;
                        
                        // clear adapters on the removed object if it is still removed
                        if (next.eContainer() != owner) {
                            next.eAdapters().clear();
                        }
                    }
                }
                
                super.doDispose();
            }};
        
        getCommandStack().execute(cmd);
        
        // the adapter is still attached, of course
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // undo/redo should not change the adapter attachment
        getCommandStack().undo();
        getCommandStack().redo();
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // flushing the command-stack should dispose the command, which should
        //   remove adapters
        getCommandStack().flush();
        assertFalse(level1.eAdapters().contains(xrefAdapter));
        
        // and the change descriptions are clean
        sniffer.assertChangesDisposed();
    }
    
    /**
     * <p>
     * Tests that the change descriptions that recorded execution, undo, and
     * redo of the removal of an element that has an ECrossReferenceAdapter
     * attached do not leak that adapter after the command stack has been
     * flushed.  This tests the disposal of <code>RecordingCommand</code>s,
     * that it clears the adapters of the change description and its contents.
     * </p>
     */
    public void test_crossReferenceAdapter_undoredo_recordingCommands() {
        // attach a cross-reference adapter to the resource set
        ECrossReferenceAdapter xrefAdapter = new ECrossReferenceAdapter();
        domain.getResourceSet().eAdapters().add(xrefAdapter);
        
        // and a transaction-sniffer to the domain
        TransactionSniffer sniffer = new TransactionSniffer(domain);
        
        final EObject level1 = find(root, "level1"); //$NON-NLS-1$
        
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        Command cmd = new RecordingCommand(domain, "Remove Branch") { //$NON-NLS-1$
            @Override
			protected void doExecute() {
                root.getBranches().remove(level1);        
            }};
        
        getCommandStack().execute(cmd);
        
        // the adapter is still attached, of course
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // undo/redo should not change the adapter attachment
        getCommandStack().undo();
        getCommandStack().redo();
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // flushing the command-stack should dispose the command, which should
        //   remove adapters from the change description and its contents
        getCommandStack().flush();
        assertFalse(level1.eAdapters().contains(xrefAdapter));
        
        // and the change descriptions are clean
        sniffer.assertChangesDisposed();
    }
    
    /**
     * <p>
     * Tests that the change descriptions that recorded execution, undo, and
     * redo of a <b>trigger command</b> that removes an element that has an
     * ECrossReferenceAdapter attached do not leak that adapter after the
     * command stack has been flushed.
     * </p><p>
     * This is a control test, using a normal EMF <code>RemoveCommand</code>
     * that has been instrumented to clear the adapters of the removed
     * element(s) upon disposal.
     * </p>
     */
    public void test_crossReferenceAdapter_undoredo_normalTriggerCommands() {
        // attach a cross-reference adapter to the resource set
        ECrossReferenceAdapter xrefAdapter = new ECrossReferenceAdapter();
        domain.getResourceSet().eAdapters().add(xrefAdapter);
        
        // and a transaction-sniffer to the domain
        TransactionSniffer sniffer = new TransactionSniffer(domain);
        
        EObject level1 = find(root, "level1"); //$NON-NLS-1$
        
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        final Command trigger = new RemoveCommand(domain, root, EXTLibraryPackage.Literals.LIBRARY__BRANCHES, level1) {
            @Override
			public void doDispose() {
                if (feature instanceof EReference && ((EReference) feature).isContainment()) {
                    for (Object o : collection) {
                        EObject next = (EObject) o;
                        
                        // clear adapters on the removed object if it is still removed
                        if (next.eContainer() != owner) {
                            next.eAdapters().clear();
                        }
                    }
                }
                
                super.doDispose();
            }};
        
        domain.addResourceSetListener(new TriggerListener() {
            @Override
			protected Command trigger(TransactionalEditingDomain domain,
                    Notification notification) {
                // trigger on the name change only
                if (notification.getFeature() == EXTLibraryPackage.Literals.LIBRARY__NAME) {
                    return trigger;
                }
                
                return null;
            }});
        
        Command cmd = domain.createCommand(SetCommand.class,
            new CommandParameter(root, EXTLibraryPackage.Literals.LIBRARY__NAME, "newname")); //$NON-NLS-1$
        
        getCommandStack().execute(cmd);
        
        // the adapter is still attached, of course
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // undo/redo should not change the adapter attachment
        getCommandStack().undo();
        getCommandStack().redo();
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // flushing the command-stack should dispose the command, which should
        //   remove adapters
        getCommandStack().flush();
        assertFalse(level1.eAdapters().contains(xrefAdapter));
        
        // and the change descriptions are clean
        sniffer.assertChangesDisposed();
    }
    
    /**
     * <p>
     * Tests that the change descriptions that recorded execution, undo, and
     * redo of a <b>trigger command</b> that removes an element that has an
     * ECrossReferenceAdapter attached do not leak that adapter after the
     * command stack has been flushed.  This tests the disposal of
     * <code>RecordingCommand</code>s, that it clears the adapters of the
     * change description and its contents.
     * </p>
     */
    public void test_crossReferenceAdapter_undoredo_recordingTriggerCommands() {
        // attach a cross-reference adapter to the resource set
        ECrossReferenceAdapter xrefAdapter = new ECrossReferenceAdapter();
        domain.getResourceSet().eAdapters().add(xrefAdapter);
        
        // and a transaction-sniffer to the domain
        TransactionSniffer sniffer = new TransactionSniffer(domain);
        
        final EObject level1 = find(root, "level1"); //$NON-NLS-1$
        
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        final Command trigger = new RecordingCommand(domain, "Remove Branch") { //$NON-NLS-1$
            @Override
			protected void doExecute() {
                root.getBranches().remove(level1);        
            }};
        
        domain.addResourceSetListener(new TriggerListener() {
            @Override
			protected Command trigger(TransactionalEditingDomain domain,
                    Notification notification) {
                // trigger on the name change only
                if (notification.getFeature() == EXTLibraryPackage.Literals.LIBRARY__NAME) {
                    return trigger;
                }
                
                return null;
            }});
        
        Command cmd = domain.createCommand(SetCommand.class,
            new CommandParameter(root, EXTLibraryPackage.Literals.LIBRARY__NAME, "newname")); //$NON-NLS-1$
        
        getCommandStack().execute(cmd);
        
        // the adapter is still attached, of course
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // undo/redo should not change the adapter attachment
        getCommandStack().undo();
        getCommandStack().redo();
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // flushing the command-stack should dispose the command, which should
        //   remove adapters
        getCommandStack().flush();
        assertFalse(level1.eAdapters().contains(xrefAdapter));
        
        // and the change descriptions are clean
        sniffer.assertChangesDisposed();
    }
	
	//
	// Fixture methods
	//
	
	protected long usedHeap() {
		Runtime rt = Runtime.getRuntime();
		
		rt.gc();
		idle(2000);
		rt.gc();
		
		long result = rt.totalMemory() - rt.freeMemory();
		
		System.out.println("Used Heap: " + (result / 1024L) + " kB"); //$NON-NLS-1$ //$NON-NLS-2$
		
		return result;
	}
	
	@SuppressWarnings("unchecked")
	static List<ChangeDescription> getChildren(CompositeChangeDescription compositeChange) {
		List<ChangeDescription> result = null;
		
		try {
			Field children = compositeChange.getClass().getDeclaredField("changes"); //$NON-NLS-1$
			children.setAccessible(true);
			
			result = (List<ChangeDescription>) children.get(compositeChange);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getLocalizedMessage());
		}
		
		return result;
	}
    
    private static class TransactionSniffer extends ResourceSetListenerImpl {
        private final TransactionalEditingDomain domain;
        private final List<ChangeDescription> changes =
        	new BasicEList.FastCompare<ChangeDescription>();
        
        TransactionSniffer(TransactionalEditingDomain domain) {
            this.domain = domain;
            domain.addResourceSetListener(this);
        }
        
        @Override
		public boolean isPostcommitOnly() {
            return true;
        }
        
        @Override
		public void resourceSetChanged(ResourceSetChangeEvent event) {
            Transaction tx = event.getTransaction();
            
            if ((tx != null) && (tx.getChangeDescription()) != null) {
                changes.add(tx.getChangeDescription());
            }
        }
        
        void assertChangesDisposed() {
            // stop listening, now
            domain.removeResourceSetListener(this);
            
            for (Iterator<EObject> iter = EcoreUtil.getAllContents(changes); iter.hasNext();) {
                EObject next = iter.next();
                assertEquals("Adapters not cleared.", 0, next.eAdapters().size()); //$NON-NLS-1$
            }
        }
    }
}
