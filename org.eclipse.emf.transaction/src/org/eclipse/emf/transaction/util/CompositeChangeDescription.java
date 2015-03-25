/**
 * <copyright>
 *
 * Copyright (c) 2005, 2015 IBM Corporation, Christian W. Damus, and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Christian W. Damus - Bug 460206
 *
 * </copyright>
 */
package org.eclipse.emf.transaction.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.BasicEMap;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.change.ChangeDescription;
import org.eclipse.emf.ecore.change.FeatureChange;
import org.eclipse.emf.ecore.change.ResourceChange;
import org.eclipse.emf.ecore.change.impl.ChangeDescriptionImpl;
import org.eclipse.emf.transaction.TransactionChangeDescription;

/**
 * A composition of zero or more change descriptions representing the
 * changes performed by transactions in a possibly nested structure.  In the
 * execution of a transaction, the changes that it makes in between nested
 * transactions and the changes made by those nested transactions are stored as
 * discrete {@link ChangeDescription}s in a composite.  The composite structure
 * is recursive.
 *
 * @author Christian W. Damus (cdamus)
 */
public class CompositeChangeDescription
	extends ChangeDescriptionImpl
	implements TransactionChangeDescription {

	private final List<ChangeDescription> changes =
		new java.util.ArrayList<ChangeDescription>();
	
	private List<ChangeDescription> detached;
	
	/**
	 * Queries whether I have no composed change descriptions.
	 * 
	 * @return <code>false</code> if I have any change descriptions;
	 *     <code>true</code>, otherwise
	 */
	public final boolean isEmpty() {
		return changes.isEmpty() && ((detached == null) || detached.isEmpty());
	}
	
	/**
	 * Removes any change descriptions that I may have.
	 */
	public final void clear() {
		changes.clear();
		detached = null;
		
		objectChanges = null;
		objectsToDetach = null;
		objectsToAttach = null;
		resourceChanges = null;
	}
	
	/**
	 * Detaches my composed change descriptions, leaving just the aggregate change model for informational purposes
	 * (I will not undo/redo anything, but I describe a bunch of changes).  This is useful for maintaining a record
	 * of changes that are encapsulated by some other means (such as, for example, in regular EMF commands that know
	 * how to undo/redo themselves).
	 * 
	 * @since 1.9
	 */
	public final void detach() {
		if (detached == null) {
			detached = new java.util.ArrayList<ChangeDescription>(changes);
		} else {
			detached.addAll(changes);
		}
		
		changes.clear();
	}
    
    /**
     * Disposes my children, recursively.
     */
    void dispose() {
		for (ChangeDescription next : changes) {
			TransactionUtil.dispose(next);
		}
		if (detached != null) {
			for (ChangeDescription next : detached) {
				TransactionUtil.dispose(next);
			}
		}
    }
	
	/**
	 * I can apply myself if all of my composed changes can apply themselves.
	 */
	public boolean canApply() {
		boolean result = true;
		
		for (Iterator<ChangeDescription> iter = changes.iterator();
				result && iter.hasNext();) {
			
			Object next = iter.next();
			
			if (next instanceof TransactionChangeDescription) {
				result = ((TransactionChangeDescription) next).canApply();
			}
		}
		
		return result;
	}
	
	// Documentation copied from the inherited method
	@Override
	public void apply() {
		// must apply changes in the reverse order that they were added
		for (ListIterator<ChangeDescription> iter = changes.listIterator(changes.size());
				iter.hasPrevious();) {
			
			iter.previous().apply();
		}
		
		changes.clear();
		detached = null;
	}

	// Documentation copied from the inherited method
	@Override
	public void applyAndReverse() {
		// must apply changes in the reverse order that they were added
		for (ListIterator<ChangeDescription> iter = changes.listIterator(changes.size());
				iter.hasPrevious();) {
			
			iter.previous().applyAndReverse();
		}
		
		// invert the order of the changes for next apply-and-reverse
		Collections.reverse(changes);
	}
	
	/**
	 * Adds a change description to me.
	 * 
	 * @param change a new change description to add
	 */
	public void add(ChangeDescription change) {
		if (!isEmpty(change)) {
			// automatically flatten composites
			if (change instanceof CompositeChangeDescription) {
				CompositeChangeDescription other = ((CompositeChangeDescription) change);
				
				for (ChangeDescription next : other.changes) {
					add(next);
				}
				if (other.detached != null) {
					for (ChangeDescription next : other.detached) {
						addDetached(next);
					}
				}
			} else {
				changes.add(change);
				appendChanges(change);
			}
		}
	}
	
	/**
	 * Adds a detached change description to me.
	 * 
	 * @param change a detached change description to add
	 */
	private void addDetached(ChangeDescription change) {
		if (!isEmpty(change)) {
			// automatically flatten composites
			if (change instanceof CompositeChangeDescription) {
				CompositeChangeDescription other = ((CompositeChangeDescription) change);
				
				for (ChangeDescription next : other.changes) {
					// The composite is detached, so also are these
					addDetached(next);
				}
				if (other.detached != null) {
					for (ChangeDescription next : other.detached) {
						addDetached(next);
					}
				}
			} else {
				if (detached == null) {
					detached = new java.util.ArrayList<ChangeDescription>();
				}
				detached.add(change);
				appendChanges(change);
			}
		}
	}
	
	private void appendChanges(ChangeDescription change) {
		if (objectChanges != null) {
			// already computed object changes.  Keep them up-to-date
			objectChanges.addAll(change.getObjectChanges());
		}
		
		if (objectsToAttach != null) {
			// already computed objects to attach.  Keep them up-to-date
			objectsToAttach.addAll(change.getObjectsToAttach());
		}
		
		if (resourceChanges != null) {
			// already computed resource changes.  Keep them up-to-date
			resourceChanges.addAll(change.getResourceChanges());
		}
	}
	
	/**
	 * Queries whether a change description has no changes.
	 * 
	 * @param changeDescription a change description (maybe <code>null</code>)
	 * 
	 * @return <code>true</code> if the specified change is <code>null</code>
	 *     or contains no changes; <code>false</code>, otherwise
	 */
	private boolean isEmpty(ChangeDescription changeDescription) {
		boolean result = changeDescription == null;
		
		if (!result) {
			if (changeDescription instanceof TransactionChangeDescription) {
				result = ((TransactionChangeDescription) changeDescription).isEmpty();
			} else {
				result = changeDescription.getObjectChanges().isEmpty()
					&& changeDescription.getObjectsToAttach().isEmpty()
					&& changeDescription.getResourceChanges().isEmpty();
			}
		}
		
		return result;
	}

	/**
	 * My object changes are the concatenation of the changes in my composed
	 * descriptions.
	 */
	@Override
	public EMap<EObject, EList<FeatureChange>> getObjectChanges() {
		if (objectChanges == null) {
			objectChanges = new BasicEMap<EObject, EList<FeatureChange>>();
			
			for (ChangeDescription next : changes) {
				objectChanges.addAll(next.getObjectChanges());
			}
		}
		
		return objectChanges;
	}

	/**
	 * My objects to detach are the union of the changes in my composed
	 * descriptions.
	 */
	@Override
	public EList<EObject> getObjectsToDetach() {
		// The core change description computes objects to detach from
		// the objects changes and resource changes. The algorithm doesn't
		// work when these lists are concatenated from composed change
		// descriptions, so we let each child compute its objects to
		// detach and take the union
		Set<EObject> result = new java.util.LinkedHashSet<EObject>();
		
		for (ChangeDescription next : changes) {
			result.addAll(next.getObjectsToDetach());
		}
		if (detached != null) {
			for (ChangeDescription next : detached) {
				result.addAll(next.getObjectsToDetach());
			}
		}
		
		return new BasicEList<EObject>(result);
	}

	/**
	 * My objects to attach are the concatenation of the changes in my composed
	 * descriptions.
	 */
	@Override
	public EList<EObject> getObjectsToAttach() {
		if (objectsToAttach == null) {
			objectsToAttach = new BasicEList<EObject>();
			
			for (ChangeDescription next : changes) {
				objectsToAttach.addAll(next.getObjectsToAttach());
			}
		}
		
		return objectsToAttach;
	}

	/**
	 * My resource changes are the concatenation of the changes in my composed
	 * descriptions.
	 */
	@Override
	public EList<ResourceChange> getResourceChanges() {
		if (resourceChanges == null) {
			resourceChanges = new BasicEList<ResourceChange>();
			
			for (ChangeDescription next : changes) {
				resourceChanges.addAll(next.getResourceChanges());
			}
		}
		
		return resourceChanges;
	}
	
	@Override
	public String toString() {
		StringBuffer result = new StringBuffer("CompositeChangeDescription["); //$NON-NLS-1$
		result.append(getObjectChanges().size()).append(", "); //$NON-NLS-1$
		result.append(getObjectsToAttach().size()).append(", "); //$NON-NLS-1$
		result.append(getObjectsToDetach().size()).append(", "); //$NON-NLS-1$
		result.append(getResourceChanges().size()).append(']');
		return result.toString();
	}
}
