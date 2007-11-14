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
 * $Id: NullOperation.java,v 1.3 2007/11/14 18:13:54 cdamus Exp $
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
