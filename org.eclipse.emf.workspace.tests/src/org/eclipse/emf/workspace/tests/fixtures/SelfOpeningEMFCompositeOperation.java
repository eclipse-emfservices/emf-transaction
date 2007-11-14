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
 * $Id: SelfOpeningEMFCompositeOperation.java,v 1.2 2007/11/14 18:13:54 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests.fixtures;

import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.ICompositeOperation;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.workspace.AbstractEMFOperation;
import org.eclipse.emf.workspace.IWorkspaceCommandStack;

/**
 * Example implementation of an EMF operation that implements the operation
 * history's notion of an "open composite." This additionally has the property
 * of being self-opening (it opens itself when it is executed).
 * 
 * @author Christian W. Damus (cdamus)
 */
public class SelfOpeningEMFCompositeOperation
    extends AbstractEMFOperation
    implements ICompositeOperation {

    private final List<IUndoableOperation> children = new java.util.ArrayList<IUndoableOperation>();

    public SelfOpeningEMFCompositeOperation(TransactionalEditingDomain domain) {
        super(domain, "EMF Composite"); //$NON-NLS-1$
    }

    @Override
	protected final IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
        throws ExecutionException {

        IStatus result;

        IOperationHistory history = ((IWorkspaceCommandStack) getEditingDomain()
            .getCommandStack()).getOperationHistory();

        // open myself
        history.openOperation(this, IOperationHistory.EXECUTE);

        try {
            result = doExecute(history, monitor, info);

            history.closeOperation(true, false, IOperationHistory.EXECUTE);
        } catch (RuntimeException e) {
            history.closeOperation(false, false, IOperationHistory.EXECUTE);
            throw e;
        }

        return result;
    }

    /**
     * Overridden by subclasses to do stuff, usually including nested executions
     * of operations on the supplied history.
     * 
     * @param history
     *            the history on which I am open and executing
     * @param monitor
     *            a progress monitor
     * @param info
     *            an info or <code>null</code>
     * 
     * @return status of delegated execution
     * @throws ExecutionException
     *             if necessary
     */
    protected IStatus doExecute(IOperationHistory history,
            IProgressMonitor monitor, IAdaptable info)
        throws ExecutionException {

        return Status.OK_STATUS;
    }

    public void add(IUndoableOperation operation) {
        children.add(operation);
        updateContexts();
    }

    public void remove(IUndoableOperation operation) {
        children.remove(operation);
        updateContexts();
    }

    /**
     * Obtains the children that have been added to me by nested executions.
     * 
     * @return my children
     */
    public List<IUndoableOperation> getChildren() {
        return children;
    }

    private void updateContexts() {
        IUndoContext[] current = getContexts();
        for (int i = 0; i < current.length; i++) {
            removeContext(current[i]);
        }
        
        Set<IUndoContext> newContexts = new java.util.HashSet<IUndoContext>();
        for (IUndoableOperation child : children) {
            IUndoContext[] next = child.getContexts();
            for (IUndoContext ctx : next) {
                if (!newContexts.add(ctx)) {
                    addContext(ctx);
                }
            }
        }
    }
}
