/**
 * Copyright (c) 2005, 2008 IBM Corporation, Zeligsoft Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bug 234868
 */
package org.eclipse.emf.workspace.tests.fixtures;

import java.util.Map;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.workspace.AbstractEMFOperation;

/**
 * A simple abstract operation for testing purposes.
 *
 * @author Christian W. Damus (cdamus)
 */
public abstract class TestOperation
	extends AbstractEMFOperation {

	private IStatus status = Status.OK_STATUS;
	
	public TestOperation(TransactionalEditingDomain domain) {
		super(domain, "Testing"); //$NON-NLS-1$
	}

	public TestOperation(TransactionalEditingDomain domain, Map<?, ?> options) {
		super(domain, "Testing", options); //$NON-NLS-1$
	}

	@Override
	protected final IStatus doExecute(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
		doExecute();
		
		return getStatus();
	}
	
	protected final IStatus getStatus() {
		return status;
	}
	
	protected final void setStatus(IStatus status) {
		this.status = status;
	}
	
	protected abstract void doExecute() throws ExecutionException;
}
