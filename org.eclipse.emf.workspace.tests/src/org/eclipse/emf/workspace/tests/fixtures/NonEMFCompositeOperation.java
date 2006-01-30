/**
 * <copyright>
 *
 * Copyright (c) 2005 IBM Corporation and others.
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
 * $Id: NonEMFCompositeOperation.java,v 1.1 2006/01/30 16:26:01 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests.fixtures;

import java.util.Iterator;
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
	
	private List children = new java.util.ArrayList();
	
	public NonEMFCompositeOperation() {
		super("Non-EMF Composite"); //$NON-NLS-1$
	}
	
	// Documentation copied from the inherited specification
	public IStatus execute(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		
		monitor.beginTask(getLabel(), children.size());
		
		try {
			for (Iterator iter = children.iterator(); iter.hasNext();) {
				((IUndoableOperation) iter.next()).execute(
						new SubProgressMonitor(monitor, 1),
						info);
			}
		} finally {
			monitor.done();
		}
		
		return Status.OK_STATUS;
	}

	// Documentation copied from the inherited specification
	public IStatus redo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		
		monitor.beginTask(getLabel(), children.size());
		
		try {
			for (Iterator iter = children.iterator(); iter.hasNext();) {
				((IUndoableOperation) iter.next()).redo(
						new SubProgressMonitor(monitor, 1),
						info);
			}
		} finally {
			monitor.done();
		}
		
		return Status.OK_STATUS;
	}

	// Documentation copied from the inherited specification
	public IStatus undo(IProgressMonitor monitor, IAdaptable info)
			throws ExecutionException {
		
		monitor.beginTask(getLabel(), children.size());
		
		try {
			for (ListIterator iter = children.listIterator(children.size()); iter.hasPrevious();) {
				((IUndoableOperation) iter.previous()).undo(
						new SubProgressMonitor(monitor, 1),
						info);
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
