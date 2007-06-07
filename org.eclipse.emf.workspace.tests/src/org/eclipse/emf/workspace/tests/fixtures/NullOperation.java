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
 * $Id: NullOperation.java,v 1.2 2007/06/07 14:26:03 cdamus Exp $
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
	
	public boolean canExecute() {
		return isExecutable;
	}
	
	public boolean canUndo() {
		return isUndoable;
	}
	
	public boolean canRedo() {
		return isRedoable;
	}
	
	// Documentation copied from the inherited specification
	public IStatus execute(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		return Status.OK_STATUS;
	}

	// Documentation copied from the inherited specification
	public IStatus redo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		return Status.OK_STATUS;
	}

	// Documentation copied from the inherited specification
	public IStatus undo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		return Status.OK_STATUS;
	}
}
