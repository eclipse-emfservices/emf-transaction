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
package org.eclipse.emf.transaction;

import org.eclipse.emf.ecore.change.ChangeDescription;

/**
 * A specialized change description that may not be able to apply itself,
 * especially in the case where it includes non-EMF changes that are not
 * reversible.
 * <p>
 * When a transaction rolls back, the assumption is that all changes that it
 * performed can be undone, even any non-EMF changes that are captured in its
 * <code>TransactionChangeDescription</code>.  This assumption is necessary because
 * the semantics of rollback guarantee that rollback cannot fail:  rollback
 * <em>must</em> restore the system to the state it was in before the
 * transaction started.  Thus, any change description (possibly nested in a
 * composite) that cannot be applied will be ignored and a best effort made to
 * apply all other changes.
 * </p>
 * <p>
 * However, the same should not hold for undo/redo of the changes performed
 * by a transaction after it has committed.  If the transaction's change
 * description is stored on some kind of "command stack" as an encapsulation of
 * an undoable change, then the possibility that a change description cannot
 * be applied should be respected by disabling undo/redo.
 * </p>
 *
 * @author Christian W. Damus (cdamus)
 */
public interface TransactionChangeDescription extends ChangeDescription {
	/**
	 * Queries whether I can {@link ChangeDescription#apply() apply} my changes.
	 * If I can, then it is assumed that I could
	 * {@link ChangeDescription#applyAndReverse() reverse} them also.
	 * 
	 * @return <code>true</code> if my changes can be applied;
	 *    <code>false</code> otherwise (i.e., they are not invertible) 
	 */
	boolean canApply();
	
	/**
	 * Queries whether I have no changes.
	 * 
	 * @return <code>true</code> if I have no changes (applying me would have
	 *     no effect on anything); <code>false</code>, otherwise
	 */
	boolean isEmpty();
}
