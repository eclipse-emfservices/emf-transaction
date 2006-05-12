package org.eclipse.emf.workspace;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.emf.transaction.ResourceSetListener;
import org.eclipse.emf.transaction.util.ConditionalRedoCommand;


/**
 * Clients may implement this command to provide {@link IUndoContext}'s for the
 *  changes that will be made with this command. This is particularly useful
 *  for clients that need to contribute non-EMF changes before a EMF transaction 
 *  is about to commit.
 *  
 * @see ResourceSetListener#transactionAboutToCommit(org.eclipse.emf.transaction.ResourceSetChangeEvent)
 */
public interface CommandWithUndoContext
	extends ConditionalRedoCommand {

	/**
	 * Returns an array of undo contexts that should be added to the IUndoableOperation
	 *  (if any) that is responsible for changes to EObjects.
	 *  
	 * @return An array of undo contexts (not null) to be added to a relevant
	 *  operation.
	 */
	IUndoContext[] getUndoContexts();
}
