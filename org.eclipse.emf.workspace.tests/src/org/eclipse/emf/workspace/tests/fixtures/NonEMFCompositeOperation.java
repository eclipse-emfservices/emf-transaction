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

import java.util.List;
import java.util.ListIterator;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.commands.operations.ICompositeOperation;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

/**
 * Example implementation of a non-EMF composite that might contain nested
 * EMF operations.
 *
 * @author Christian W. Damus (cdamus)
 */
public class NonEMFCompositeOperation
		extends AbstractOperation
		implements ICompositeOperation {
	
	private final List<IUndoableOperation> children = new java.util.ArrayList<IUndoableOperation>();
	
	public NonEMFCompositeOperation() {
		super("Non-EMF Composite"); //$NON-NLS-1$
	}
	
	// Documentation copied from the inherited specification
	@Override
	public IStatus execute(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		
		monitor.beginTask(getLabel(), children.size());
		
		try {
			for (IUndoableOperation next : children) {
				next.execute(new SubProgressMonitor(monitor, 1), info);
			}
		} finally {
			monitor.done();
		}
		
		return Status.OK_STATUS;
	}

	// Documentation copied from the inherited specification
	@Override
	public IStatus redo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		
		monitor.beginTask(getLabel(), children.size());
		
		try {
			for (IUndoableOperation next : children) {
				next.redo(new SubProgressMonitor(monitor, 1), info);
			}
		} finally {
			monitor.done();
		}
		
		return Status.OK_STATUS;
	}

	// Documentation copied from the inherited specification
	@Override
	public IStatus undo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		
		monitor.beginTask(getLabel(), children.size());
		
		try {
			for (ListIterator<IUndoableOperation> iter = children.listIterator(children.size());
					iter.hasPrevious();) {
				iter.previous().undo(new SubProgressMonitor(monitor, 1), info);
			}
		} finally {
			monitor.done();
		}
		
		return Status.OK_STATUS;
	}

	public void add(IUndoableOperation operation) {
		children.add(operation);
	}
	
	public void remove(IUndoableOperation operation) {
		children.remove(operation);
	}
}
