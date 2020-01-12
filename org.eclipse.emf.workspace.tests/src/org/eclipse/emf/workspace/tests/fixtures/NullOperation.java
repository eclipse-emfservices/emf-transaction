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
package org.eclipse.emf.workspace.tests.fixtures;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * An operation that does nothing.
 *
 * @author Christian W. Damus (cdamus)
 */
public class NullOperation extends AbstractOperation {
	private final boolean isExecutable;
	private final boolean isUndoable;
	private final boolean isRedoable;
	
	public NullOperation() {
		this(true, true, true);
	}
	
	public NullOperation(boolean isExecutable) {
		this(isExecutable, true, true);
	}
	
	public NullOperation(boolean isExecutable, boolean isUndoable) {
		this(isExecutable, isUndoable, true);
	}
	
	public NullOperation(boolean isExecutable, boolean isUndoable, boolean isRedoable) {
		super("Null"); //$NON-NLS-1$

		this.isExecutable = isExecutable;
		this.isUndoable = isUndoable;
		this.isRedoable = isRedoable;
	}
	
	@Override
	public boolean canExecute() {
		return isExecutable;
	}
	
	@Override
	public boolean canUndo() {
		return isUndoable;
	}
	
	@Override
	public boolean canRedo() {
		return isRedoable;
	}
	
	// Documentation copied from the inherited specification
	@Override
	public IStatus execute(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		return Status.OK_STATUS;
	}

	// Documentation copied from the inherited specification
	@Override
	public IStatus redo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		return Status.OK_STATUS;
	}

	// Documentation copied from the inherited specification
	@Override
	public IStatus undo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		return Status.OK_STATUS;
	}
}
