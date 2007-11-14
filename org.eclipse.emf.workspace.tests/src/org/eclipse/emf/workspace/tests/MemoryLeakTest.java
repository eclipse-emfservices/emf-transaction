/**
 * <copyright>
 *
 * Copyright (c) 2007 IBM Corporation and others.
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
 * $Id: MemoryLeakTest.java,v 1.2 2007/11/14 18:13:54 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests;

import java.util.Iterator;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.UndoContext;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
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
import org.eclipse.emf.workspace.AbstractEMFOperation;
import org.eclipse.emf.workspace.EMFOperationCommand;
import org.eclipse.emf.workspace.ResourceUndoContext;


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
     * <p>
     * Tests that the change descriptions that recorded execution, undo, and
     * redo of the removal of an element that has an ECrossReferenceAdapter
     * attached do not leak that adapter after the command stack has been
     * flushed.
     * </p><p>
     * This is a control test, using a normal EMF <code>RemoveCommand</code>
     * that has been instrumented to clear the adapters of the removed
     * element(s) upon disposal.
     * </p><p>
     * This test exercises the workspace command stack, not the operation
     * history directly.
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
        
        // remove the resource undo context so that flush will dispose
        ResourceUndoContext resctx = new ResourceUndoContext(domain, testResource);
        history.getUndoOperation(resctx).removeContext(resctx);
        
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
     * </p><p>
     * This test exercises the workspace command stack, not the operation
     * history directly.
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
        
        // remove the resource undo context so that flush will dispose
        ResourceUndoContext resctx = new ResourceUndoContext(domain, testResource);
        history.getUndoOperation(resctx).removeContext(resctx);
        
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
     * </p><p>
     * This test exercises the workspace command stack, not the operation
     * history directly.
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
        
        // remove the resource undo context so that flush will dispose
        ResourceUndoContext resctx = new ResourceUndoContext(domain, testResource);
        history.getUndoOperation(resctx).removeContext(resctx);
        
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
     * </p><p>
     * This test exercises the workspace command stack, not the operation
     * history directly.
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
        
        // remove the resource undo context so that flush will dispose
        ResourceUndoContext resctx = new ResourceUndoContext(domain, testResource);
        history.getUndoOperation(resctx).removeContext(resctx);
        
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
     * attached do not leak that adapter after the operation history has been
     * flushed.
     * </p>
     */
    public void test_crossReferenceAdapter_undoredo_operations() {
        // attach a cross-reference adapter to the resource set
        ECrossReferenceAdapter xrefAdapter = new ECrossReferenceAdapter();
        domain.getResourceSet().eAdapters().add(xrefAdapter);
        
        // and a transaction-sniffer to the domain
        TransactionSniffer sniffer = new TransactionSniffer(domain);
        
        final EObject level1 = find(root, "level1"); //$NON-NLS-1$
        
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        IUndoableOperation oper = new AbstractEMFOperation(domain, "Remove Branch") { //$NON-NLS-1$
            @Override
			protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
                throws ExecutionException {
                
                root.getBranches().remove(level1);
                
                return Status.OK_STATUS;
            }};
        
        IUndoContext ctx = new UndoContext();
        oper.addContext(ctx);
        
        try {
            history.execute(oper, null, null);
        } catch (ExecutionException e) {
            fail("Failed to execute operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        
        // remove the resource undo context so that flush will dispose
        ResourceUndoContext resctx = new ResourceUndoContext(domain, testResource);
        history.getUndoOperation(resctx).removeContext(resctx);
        
        // the adapter is still attached, of course
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // undo/redo should not change the adapter attachment
        try {
            history.undo(ctx, null, null);
        } catch (ExecutionException e) {
            fail("Failed to undo operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        try {
            history.redo(ctx, null, null);
        } catch (ExecutionException e) {
            fail("Failed to redo operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // flushing the context should dispose the operation, which should
        //   remove adapters from the change description and its contents
        history.dispose(ctx, true, true, true);
        assertFalse(level1.eAdapters().contains(xrefAdapter));
        
        // and the change descriptions are clean
        sniffer.assertChangesDisposed();
    }
    
    /**
     * <p>
     * Tests that the change descriptions that recorded execution, undo, and
     * redo of a <b>trigger command</b> that removes an element that has an
     * ECrossReferenceAdapter attached do not leak that adapter after the
     * operation history has been flushed.  This tests the disposal of
     * <code>RecordingCommand</code>s, that it clears the adapters of the
     * change description and its contents.
     * </p>
     */
    public void test_crossReferenceAdapter_undoredo_operationTriggerCommands() {
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
        
        IUndoableOperation oper = new AbstractEMFOperation(domain, "Rename Library") { //$NON-NLS-1$
            @Override
			protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
                throws ExecutionException {
                
                root.setName("newname"); //$NON-NLS-1$
                
                return Status.OK_STATUS;
            }};
        
        IUndoContext ctx = new UndoContext();
        oper.addContext(ctx);
        
        try {
            history.execute(oper, null, null);
        } catch (ExecutionException e) {
            fail("Failed to execute operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        
        // remove the resource undo context so that flush will dispose
        ResourceUndoContext resctx = new ResourceUndoContext(domain, testResource);
        history.getUndoOperation(resctx).removeContext(resctx);
        
        // the adapter is still attached, of course
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // undo/redo should not change the adapter attachment
        try {
            history.undo(ctx, null, null);
        } catch (ExecutionException e) {
            fail("Failed to undo operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        try {
            history.redo(ctx, null, null);
        } catch (ExecutionException e) {
            fail("Failed to redo operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // flushing the context should dispose the operation, which should
        //   remove adapters from the change description and its contents
        history.dispose(ctx, true, true, true);
        assertFalse(level1.eAdapters().contains(xrefAdapter));
        
        // and the change descriptions are clean
        sniffer.assertChangesDisposed();
    }
    
    /**
     * <p>
     * Tests that the change descriptions that recorded execution, undo, and
     * redo of a <b>trigger operation</b> that removes an element that has an
     * ECrossReferenceAdapter attached do not leak that adapter after the
     * operation history has been flushed.  This tests the disposal of
     * <code>RecordingCommand</code>s, that it clears the adapters of the
     * change description and its contents.
     * </p>
     */
    public void test_crossReferenceAdapter_undoredo_operationTriggerOperations() {
        // attach a cross-reference adapter to the resource set
        ECrossReferenceAdapter xrefAdapter = new ECrossReferenceAdapter();
        domain.getResourceSet().eAdapters().add(xrefAdapter);
        
        // and a transaction-sniffer to the domain
        TransactionSniffer sniffer = new TransactionSniffer(domain);
        
        final EObject level1 = find(root, "level1"); //$NON-NLS-1$
        
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        IUndoableOperation triggerOper = new AbstractEMFOperation(domain, "Remove Branch") { //$NON-NLS-1$
            @Override
			protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
                throws ExecutionException {
                
                root.getBranches().remove(level1);  
                
                return Status.OK_STATUS;
            }};
        final Command trigger = new EMFOperationCommand(domain, triggerOper);
        
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
        
        IUndoableOperation oper = new AbstractEMFOperation(domain, "Rename Library") { //$NON-NLS-1$
            @Override
			protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
                throws ExecutionException {
                
                root.setName("newname"); //$NON-NLS-1$
                
                return Status.OK_STATUS;
            }};
        
        IUndoContext ctx = new UndoContext();
        oper.addContext(ctx);
        
        try {
            history.execute(oper, null, null);
        } catch (ExecutionException e) {
            fail("Failed to execute operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        
        // remove the resource undo context so that flush will dispose
        ResourceUndoContext resctx = new ResourceUndoContext(domain, testResource);
        history.getUndoOperation(resctx).removeContext(resctx);
        
        // the adapter is still attached, of course
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // undo/redo should not change the adapter attachment
        try {
            history.undo(ctx, null, null);
        } catch (ExecutionException e) {
            fail("Failed to undo operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        try {
            history.redo(ctx, null, null);
        } catch (ExecutionException e) {
            fail("Failed to redo operation: " + e.getLocalizedMessage()); //$NON-NLS-1$
        }
        assertTrue(level1.eAdapters().contains(xrefAdapter));
        
        // flushing the context should dispose the operation, which should
        //   remove adapters from the change description and its contents
        history.dispose(ctx, true, true, true);
        assertFalse(level1.eAdapters().contains(xrefAdapter));
        
        // and the change descriptions are clean
        sniffer.assertChangesDisposed();
    }
    
    //
    // Framework methods
    //
    
    private static class TransactionSniffer extends ResourceSetListenerImpl {
        private final TransactionalEditingDomain domain;
        private final List<ChangeDescription> changes =
        	new java.util.ArrayList<ChangeDescription>();
        
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
