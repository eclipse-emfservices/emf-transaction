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

import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.OperationHistoryEvent;

/**
 * A one-shot listener that will listen for the next operation to be executed
 * and then adds a specified context to it and removes itself from the history.
 *
 * @author Christian W. Damus (cdamus)
 */
public class ContextAdder implements IOperationHistoryListener {
	private IUndoContext context;

	public ContextAdder(IUndoContext contextToAdd) {
		context = contextToAdd;
	}
	
	/**
	 * Adds my context to an operation when it is done then removes me from the
	 * history.
	 */
	public void historyNotification(OperationHistoryEvent event) {
		switch (event.getEventType()) {
		case OperationHistoryEvent.DONE:
			event.getOperation().addContext(context);
			// fall-through is intentional
		case OperationHistoryEvent.OPERATION_NOT_OK:
			event.getHistory().removeOperationHistoryListener(this);
			break;
		}
	}
}
