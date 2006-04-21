/**
 * <copyright>
 *
 * Copyright (c) 2006 IBM Corporation and others.
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
 * $Id: TransactionUtil.java,v 1.2 2006/04/21 14:59:10 cdamus Exp $
 */
package org.eclipse.emf.transaction.util;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EContentsEList;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;

/**
 * Static utilities for dealing with EMF elements and resources in a
 * transactional editing domain.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TransactionUtil {
	/** Cannot instantiate. */
	private TransactionUtil() {
		super();
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * element.
	 * 
	 * @param eObject an EMF model element
	 * 
	 * @return its editing domain, or <code>null</code> if it is not attached
	 *     to any resource in an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(EObject eObject) {
		TransactionalEditingDomain result = null;
		Resource res = eObject.eResource();
		
		if (res != null) {
			result = getEditingDomain(res);
		}
		
		return result;
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * resource.
	 * 
	 * @param resource a resource
	 * 
	 * @return its editing domain, or <code>null</code> if it is not in a
	 *     resource set managed by an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(Resource resource) {
		TransactionalEditingDomain result = null;
		ResourceSet rset = resource.getResourceSet();
		
		if (rset != null) {
			result = getEditingDomain(rset);
		}
		
		return result;
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * resource set.
	 * 
	 * @param rset a resource set
	 * 
	 * @return its editing domain, or <code>null</code> if it is managed by
	 *     an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(ResourceSet rset) {
		return TransactionalEditingDomain.Factory.INSTANCE.getEditingDomain(rset);
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * object.  This is useful, for example, to get the editing domain from
	 * which a {@link org.eclipse.emf.common.notify.Notification} originated by
	 * testing its
	 * {@link org.eclipse.emf.common.notify.Notification#getNotifier() notifier}.
	 * 
	 * @param object some object
	 * 
	 * @return its editing domain, or <code>null</code> if it is not of a type
	 *     that is managed by an editing domain or if it is of an appropriate
	 *     type but happens not to be managed by an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(Object object) {
		TransactionalEditingDomain result = null;
		
		if (object instanceof EObject) {
			result = getEditingDomain((EObject) object);
		} else if (object instanceof Resource) {
			result = getEditingDomain((Resource) object);
		} else if (object instanceof ResourceSet) {
			result = getEditingDomain((ResourceSet) object);
		} else if (object instanceof Transaction) {
			result = ((Transaction) object).getEditingDomain();
		} else if (object instanceof TransactionalEditingDomain) {
			result = (TransactionalEditingDomain) object;
		} else if (object instanceof IEditingDomainProvider) {
			EditingDomain domain = ((IEditingDomainProvider) object).getEditingDomain();
			
			if (domain instanceof TransactionalEditingDomain) {
				result = (TransactionalEditingDomain) domain;
			}
		}
		
		return result;
	}
	
	/**
	 * Obtains a dynamic view on the "proper" contents of an {@link EObject}.
	 * These are the contained objects that are stored in the same resource
	 * as the <code>eobject</code>.
	 * 
	 * @param eobject a model element
	 * @return all of its directly- and properly-contained elements
	 */
	public static EList getProperContents(EObject eobject) {
		return new EContentsEList(eobject) {

			protected boolean resolve() {
				return false;
			}
			
			public int size() {
				int result = 0;
				
				for (Iterator iter = newNonResolvingListIterator(); iter.hasNext(); iter.next()) {
					result++;
				}
				
				return result;
			}
			
			public boolean isEmpty() {
				return size() == 0;
			}
			
			public Object basicGet(int index) {
				Object result = null;
				
				if (index < 0) {
					throw new IndexOutOfBoundsException();
				}
				
				try {
					Iterator iter = newNonResolvingListIterator();
					for (int i = 0; i <= index; i++) {
						result = iter.next();
					}
				} catch (NoSuchElementException e) {
					throw new IndexOutOfBoundsException();
				}
				
				return result;
			}
			
			protected ListIterator newNonResolvingListIterator() {
				final FeatureListIterator delegate =
					(FeatureListIterator) super.newNonResolvingListIterator();
				
				return new FeatureListIterator() {
					private Object nextResult = null;
					private int nextIndex = 0;
					private Object previousResult = null;
					private int previousIndex = -1;
					
					private boolean isCrossResourceContained(Object obj) {
						InternalEObject eobj = (InternalEObject) obj;
						
						return eobj.eIsProxy() || (eobj.eDirectResource() != null);
					}
					
					public boolean hasNext() {
						while ((nextResult == null) && delegate.hasNext()) {
							nextResult = delegate.next();
							
							if (isCrossResourceContained(nextResult)) {
								nextResult = null;
							}
						}
						
						return nextResult != null;
					}

					public Object next() {
						if (!hasNext()) {
							// must check hasNext() in order to initialize
							//    the nextResult
							throw new NoSuchElementException();
						}
						
						Object result = nextResult;
						nextResult = null;
						previousResult = null;
						nextIndex++;
						previousIndex++;
						return result;
					}

					public boolean hasPrevious() {
						while ((previousResult == null) && delegate.hasPrevious()) {
							previousResult = delegate.previous();
							
							if (isCrossResourceContained(previousResult)) {
								previousResult = null;
							}
						}
						
						return previousResult != null;
					}

					public Object previous() {
						if (!hasPrevious()) {
							// must check hasPrevious() in order to initialize
							//    the previousResult
							throw new NoSuchElementException();
						}
						
						Object result = previousResult;
						previousResult = null;
						nextResult = null;
						nextIndex--;
						previousIndex--;
						return result;
					}

					public int nextIndex() {
						return nextIndex;
					}

					public int previousIndex() {
						return previousIndex;
					}
					
					public EStructuralFeature feature() {
						return delegate.feature();
					}

					public void remove() {
						delegate.remove();
					}

					public void add(Object arg0) {
						delegate.add(arg0);
					}

					public void set(Object arg0) {
						delegate.set(arg0);
					}
				};
			}};
	}
}
