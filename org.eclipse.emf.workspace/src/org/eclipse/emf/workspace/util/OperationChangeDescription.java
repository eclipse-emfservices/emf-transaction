/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.workspace.util;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.impl.ChangeDescriptionImpl;
import org.eclipse.emf.transaction.TransactionChangeDescription;
import org.eclipse.emf.workspace.internal.EMFWorkspacePlugin;
import org.eclipse.emf.workspace.internal.EMFWorkspaceStatusCodes;
import org.eclipse.emf.workspace.internal.l10n.Messages;
import org.eclipse.osgi.util.NLS;

/**
 * A change description that simply wraps an {@link IUndoableOperation}, asking
 * it to undo or redo when {@link ChangeDescription#applyAndReverse() applying}.
 * <p>
 * <b>Note</b> that this class is not intended to be used by clients.  It is
 * only needed by service providers extending this API.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 */
public class OperationChangeDescription
		extends ChangeDescriptionImpl
		implements TransactionChangeDescription {
	private boolean isRedone = true;
	private IUndoableOperation operation;
	private Reference<IAdaptable> info;

	/**
	 * Initializes me with the undoable operation that I encapsulate and the
	 * adaptable with which it was originally executed.
	 * 
	 * @param operation the operation that I encapsulate
	 * @param info the adaptable to provide when undoing or redoing the operation
	 */
	public OperationChangeDescription(IUndoableOperation operation, IAdaptable info) {
		this.operation = operation;
		
		this.info = new WeakReference<IAdaptable>(info);
	}
	
	/**
	 * I can apply if my wrapped operation can undo or redo, according to whether
	 * it was last undone or redone.
	 * 
	 * @return <code>true</code> if my operation can undo/redo;
	 *    <code>false</code>, otherwise
	 *    
	 * @see IUndoableOperation#canUndo()
	 * @see IUndoableOperation#canRedo()
	 */
	public boolean canApply() {
		return (operation != null)
				&& (isRedone? operation.canUndo() : operation.canRedo());
	}
	
	/**
	 * I apply my change by undoing the encapsulated operation.  After it is
	 * undone, I dispose myself.
	 */
	@Override
	public void apply() {
		try {
			operation.undo(new NullProgressMonitor(), info.get());
		} catch (ExecutionException e) {
			EMFWorkspacePlugin.INSTANCE.log(new Status(
				IStatus.ERROR,
				EMFWorkspacePlugin.getPluginId(),
				EMFWorkspaceStatusCodes.ROLLBACK_FAILED,
				NLS.bind(Messages.rollbackFailed, operation.getLabel()),
				e));
		} finally {
			dispose();
		}
	}

	/**
	 * I apply-and-reverse by alternately undoing and redoing the encapsulated
	 * operation.
	 */
	@Override
	public void applyAndReverse() {
		try {
			if (isRedone) {
				operation.undo(new NullProgressMonitor(), info.get());
				isRedone = false;
			} else {
				operation.redo(new NullProgressMonitor(), info.get());
				isRedone = true;
			}
		} catch (ExecutionException e) {
			EMFWorkspacePlugin.INSTANCE.log(new Status(
				IStatus.ERROR,
				EMFWorkspacePlugin.getPluginId(),
				EMFWorkspaceStatusCodes.ROLLBACK_FAILED,
				NLS.bind(Messages.rollbackFailed, operation.getLabel()),
				e));
		}
	}
	
	/**
	 * I can only assume that the operation I wrap makes some kind of change.
	 * 
	 * @return <code>false</code>, always
	 */
	public boolean isEmpty() {
		return false;
	}
	
	/**
	 * Forgets my operation and clears my reference to the adaptable.
	 */
	public void dispose() {
		operation = null;
		
		if (info != null) {
			info.clear();
			info = null;
		}
	}
}
