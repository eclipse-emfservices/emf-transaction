/**
 * <copyright>
 *
 * Copyright (c) 2006, 2008 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *   Mario Winterer - 225068 Memory leak in part listener management 
 *
 * </copyright>
 *
 * $Id: ActionWrapperHelper.java,v 1.3 2008/04/07 12:58:38 cdamus Exp $
 */
package org.eclipse.emf.workspace.ui.actions;

import java.util.Map;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.operations.OperationHistoryActionHandler;

/**
 * Helper object for the undo and redo action wrappers, to share common behaviour
 * that they cannot inherit because they must separately extend the EMF
 * action classes.  i.e., this is an instance of the "prefer composition over
 * inheritance" idiom.
 * 
 * @param <T> the operation-history action handler type that I support
 * 
 * @author Christian W. Damus (cdamus)
 */
class ActionWrapperHelper<T extends OperationHistoryActionHandler> extends Action {
	private final OwnerAccess<T> ownerAccess;
	
	private T delegate;
	
	private final Map<IWorkbenchPartSite, T> siteToActionHandler =
		new java.util.HashMap<IWorkbenchPartSite, T>();
	private IPartListener partListener;
	
	private final IPropertyChangeListener listener = new IPropertyChangeListener() {
		public void propertyChange(PropertyChangeEvent event) {
			// propagate to my owner's listeners
			ownerAccess.firePropertyChange(
					event.getProperty(), event.getOldValue(), event.getNewValue());
		}};

	ActionWrapperHelper(OwnerAccess<T> ownerAccess) {
		this.ownerAccess = ownerAccess;
	}
		
	/**
	 * Extends the superclass implementation to update the operation history
	 * undo action handler to which I delegate.
	 */
	void setActiveWorkbenchPart(IWorkbenchPart workbenchPart) {
		IUndoContext context = null;
		
		if (workbenchPart != null) {
			context = (IUndoContext) workbenchPart.getAdapter(IUndoContext.class);
		}
		
		if (context != null) {
			if (delegate != null) {
				delegate.removePropertyChangeListener(listener);
			}
			
			delegate = getActionHandler(workbenchPart.getSite(), context);
			delegate.addPropertyChangeListener(listener);
			
			// force enablement update in UI
			boolean enabled = isEnabled();
			ownerAccess.firePropertyChange(
					IAction.ENABLED,
					Boolean.valueOf(!enabled),
					Boolean.valueOf(enabled));
		}
	}
	
	private T getActionHandler(IWorkbenchPartSite site, IUndoContext context) {
		T result = siteToActionHandler.get(site);
		
		if (result == null) {
			result = createActionHandler(site, context);
		}
		
		return result;
	}
	
	/**
	 * Creates and returns a action handler for the given workbench-site with the
	 * given undo context. The created action handler will be stored in the
	 * siteToActionHandler map for further use.
	 * 
	 * @param site
	 *            The site to create an action handler for.
	 * @param context
	 *            The undo context the action handler should use.
	 * @return the new action handler
	 */
	private T createActionHandler(IWorkbenchPartSite site, IUndoContext context) {
		T result = ownerAccess.createDelegate(site, context);

		if (!actionHandlerExists(site.getPage())) {
			// there are no handlers for the given page so far;
			// one will be added below so we must listen to closing parts
			// on this page to update the siteToActionHandler map.
			enablePartListener(site.getPage());
		}

		siteToActionHandler.put(site, result);

		return result;
	}

	/**
	 * Removes the action handler for the given workbench-site (if existing) from the
	 * site to action-handler map.
	 * 
	 * @param site the part site from which to remove the action handler
	 */
	private void removeActionHandler(IWorkbenchPartSite site) {
		T handler = siteToActionHandler.get(site);

		if (handler != null) {
			siteToActionHandler.remove(site);

			if (!actionHandlerExists(site.getPage())) {
				// no more action handler => we do not need to listen to part
				// changes
				disablePartListener(site.getPage());
			}

			// don't dispose the handler, because it's listening for the
			// part closures, too
		}
	}

	/**
	 * Tests if there is a mapping to an action handler for any part of the given
	 * page.
	 * 
	 * @param page a workbench page
	 * @return whether this page currently has any action handler
	 */
	private boolean actionHandlerExists(IWorkbenchPage page) {
		for (IWorkbenchPartSite site : siteToActionHandler.keySet()) {
			if (site.getPage() == page) {
				return true;
			}
		}

		return false;
	}

	private void enablePartListener(IWorkbenchPage page) {
		// according to javadoc of addPartListener, the part listener will not
		// be added twice.
		page.addPartListener(getPartListener());
	}

	private void disablePartListener(IWorkbenchPage page) {
		page.removePartListener(getPartListener());
	}

	/**
	 * Obtains a part listener that will remove the mapping of part-site to
	 * action handler when a part is closed.
	 * 
	 * @return my part listener
	 */
	private IPartListener getPartListener() {
		if (partListener == null) {
			partListener = new IPartListener() {
			
				public void partClosed(IWorkbenchPart part) {
					removeActionHandler(part.getSite());
				}
			
				public void partOpened(IWorkbenchPart part) {
					// not interesting
				}
			
				public void partDeactivated(IWorkbenchPart part) {
					// not interesting
				}
			
				public void partBroughtToTop(IWorkbenchPart part) {
					// not interesting
				}
			
				public void partActivated(IWorkbenchPart part) {
					// not interesting
				}};
		}
		
		return partListener;
	}
	
	/**
	 * Delegates to the operation framework action handler.
	 */
	public void update() {
		if (delegate != null) {
			delegate.update();
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public String getDescription() {
		if (delegate != null) {
			return delegate.getDescription();
		} else {
			return null;
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public String getText() {
		if (delegate != null) {
			return delegate.getText();
		} else {
			return null;
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public String getToolTipText() {
		if (delegate != null) {
			return delegate.getToolTipText();
		} else {
			return null;
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public boolean isEnabled() {
		if (delegate != null) {
			return delegate.isEnabled();
		} else {
			return false;
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public boolean isHandled() {
		if (delegate != null) {
			return delegate.isHandled();
		} else {
			return false;
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void run() {
		if (delegate != null) {
			delegate.run();
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void runWithEvent(Event event) {
		if (delegate != null) {
			delegate.runWithEvent(event);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void setChecked(boolean checked) {
		if (delegate != null) {
			delegate.setChecked(checked);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void setDescription(String text) {
		if (delegate != null) {
			delegate.setDescription(text);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void setEnabled(boolean enabled) {
		if (delegate != null) {
			delegate.setEnabled(enabled);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void setText(String text) {
		if (delegate != null) {
			delegate.setText(text);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void setToolTipText(String toolTipText) {
		if (delegate != null) {
			delegate.setToolTipText(toolTipText);
		}
	}
	
	/**
	 * Interface of an object that provides private access to the action wrapper
	 * that owns me.
	 *
	 * @author Christian W. Damus (cdamus)
	 */
	static interface OwnerAccess<T extends Action> {
		/**
		 * Fires a property change event on behalf of the owner action wrapper.
		 * 
		 * @param property the property to fire
		 * @param oldValue the property's old value
		 * @param newValue the property's new value
		 */
		void firePropertyChange(String property, Object oldValue, Object newValue);
		
		/**
		 * Creates an operation history action handler of the appropriate
		 * undo or redo variety, to which I will delegate.
		 * 
		 * @param site the site for the action handler
		 * @param context the action handler's undo context
		 * 
		 * @return the new action handler
		 */
		T createDelegate(IWorkbenchPartSite site, IUndoContext context);
	}
}
