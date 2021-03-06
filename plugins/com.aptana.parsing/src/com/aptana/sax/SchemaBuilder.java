/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.sax;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * @author Kevin Lindsey
 */
public final class SchemaBuilder extends ValidatingReader
{
	private static final String USAGE_ATTRIBUTE = "usage"; //$NON-NLS-1$
	private static final String HAS_TEXT_ATTRIBUTE = "hasText"; //$NON-NLS-1$
	private static final String ON_EXIT_ATTRIBUTE = "onExit"; //$NON-NLS-1$
	private static final String ON_ENTER_ATTRIBUTE = "onEnter"; //$NON-NLS-1$
	private static final String TYPE_ATTRIBUTE = "type"; //$NON-NLS-1$
	private static final String NAME_ATTRIBUTE = "name"; //$NON-NLS-1$
	private static final String ALLOW_FREEFORM_MARKUP_ATTRIBUTE = "allowFreeformMarkup"; //$NON-NLS-1$
	private static final String USE_ELEMENT_SET_ELEMENT = "use-element-set"; //$NON-NLS-1$
	private static final String ELEMENT_SET_ELEMENT = "element-set"; //$NON-NLS-1$
	private static final String SETS_ELEMENT = "sets"; //$NON-NLS-1$
	private static final String CHILD_ELEMENT_ELEMENT = "child-element"; //$NON-NLS-1$
	private static final String ATTRIBUTE_ELEMENT = "attribute"; //$NON-NLS-1$
	private static final String OPTIONAL = "optional"; //$NON-NLS-1$
	private static final String REQUIRED = "required"; //$NON-NLS-1$
	private static final String ELEMENT_ELEMENT = "element"; //$NON-NLS-1$
	private static final String SCHEMA_ELEMENT = "schema"; //$NON-NLS-1$

	private static String SCHEMA_1_0_NAMESPACE = "http://www.aptana.com/2005/schema/1.0"; //$NON-NLS-1$
	private static String SCHEMA_1_1_NAMESPACE = "http://www.aptana.com/2007/schema/1.1"; //$NON-NLS-1$

	private Schema _newSchema;
	private Stack<ISchemaElement> _elementStack;
	private ISchemaElement _currentElement;

	private String _currentSetId;
	private Map<String, ISchemaElement> _sets;

	private Schema _versionSelectorSchema;
	private Schema _schema10;
	private Schema _schema11;

	/**
	 * Create a new instance of SchemaParser
	 * 
	 * @param schema
	 * @throws SchemaInitializationException
	 */
	private SchemaBuilder() throws SchemaInitializationException
	{
		this._elementStack = new Stack<ISchemaElement>();
		this._sets = new HashMap<String, ISchemaElement>();

		try
		{
			buildSchemaSchemas();
		}
		catch (SecurityException e)
		{
			String msg = Messages.SchemaBuilder_Insufficient_Reflection_Security;
			SchemaInitializationException ie = new SchemaInitializationException(msg, e);

			throw ie;
		}
		catch (NoSuchMethodException e)
		{
			String msg = Messages.SchemaBuilder_Missing_Handler_Method;
			SchemaInitializationException ie = new SchemaInitializationException(msg, e);

			throw ie;
		}
	}

	/**
	 * Create the state machine that loads and recognizes our schema xml format
	 * 
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	private void buildSchemaSchemas() throws SecurityException, NoSuchMethodException
	{
		// create 1.0 schema schema
		this._schema10 = this.buildSchema10Schema();

		// create 1.1 schema schema
		this._schema11 = this.buildSchema11Schema();

		// create schema schema to select the proper version schema
		this._versionSelectorSchema = this.buildVersionSelectorSchema();
	}

	/**
	 * buildVersionSelectorSchema
	 * 
	 * @return Schema
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 */
	private Schema buildVersionSelectorSchema() throws SecurityException, NoSuchMethodException
	{
		Schema result = new Schema(this);

		// create single element schema used to detect which schema version to use
		// to process the current schema definition file

		// create root element
		ISchemaElement root = result.createElement(SCHEMA_ELEMENT);

		// tell schema this is the root
		result.setRootElement(SCHEMA_ELEMENT);

		// set schema's onEnter handler
		root.setOnEnter("startSchemaElement"); //$NON-NLS-1$

		return result;
	}

	/**
	 * buildSchema10Schema
	 * 
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 */
	private Schema buildSchema10Schema() throws SecurityException, NoSuchMethodException
	{
		Schema result = new Schema(this);

		// create the root element
		ISchemaElement root = result.createElement(SCHEMA_ELEMENT);

		// tell schema this is the root
		result.setRootElement(SCHEMA_ELEMENT);

		// create element element and add as child of root
		ISchemaElement element = result.createElement(ELEMENT_ELEMENT);
		root.addTransition(element);

		// set element's attributes
		element.addAttribute(NAME_ATTRIBUTE, REQUIRED);
		element.addAttribute(TYPE_ATTRIBUTE, OPTIONAL);
		element.addAttribute(ON_ENTER_ATTRIBUTE, OPTIONAL);
		element.addAttribute(ON_EXIT_ATTRIBUTE, OPTIONAL);
		element.addAttribute(HAS_TEXT_ATTRIBUTE, OPTIONAL);
		element.addAttribute(ALLOW_FREEFORM_MARKUP_ATTRIBUTE, OPTIONAL);

		// set element's onEnter and onExit handlers
		element.setOnEnter("startElementElement"); //$NON-NLS-1$
		element.setOnExit("exitElementElement"); //$NON-NLS-1$

		// create attribute element and add as child of element
		ISchemaElement attribute = result.createElement(ATTRIBUTE_ELEMENT);
		element.addTransition(attribute);

		// set attribute's attributes
		attribute.addAttribute(NAME_ATTRIBUTE, REQUIRED);
		attribute.addAttribute(USAGE_ATTRIBUTE, OPTIONAL);

		// set attribute element's onEnter handler
		attribute.setOnEnter("startAttributeElement"); //$NON-NLS-1$

		// create child-element element and add as child of element
		ISchemaElement childElement = result.createElement(CHILD_ELEMENT_ELEMENT);
		element.addTransition(childElement);

		// set child-element's attributes
		childElement.addAttribute(NAME_ATTRIBUTE, REQUIRED);

		// set child-element's onEnter handler
		childElement.setOnEnter("startChildElementElement"); //$NON-NLS-1$

		return result;
	}

	/**
	 * buildSchema10Schema
	 * 
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 */
	private Schema buildSchema11Schema() throws SecurityException, NoSuchMethodException
	{
		Schema result = new Schema(this);

		// create the root element
		ISchemaElement root = result.createElement(SCHEMA_ELEMENT);

		// tell schema this is the root
		result.setRootElement(SCHEMA_ELEMENT);

		// create element element and add as child of root
		ISchemaElement element = result.createElement(ELEMENT_ELEMENT);
		root.addTransition(element);

		// set element's attributes
		element.addAttribute(NAME_ATTRIBUTE, REQUIRED);
		element.addAttribute(ON_ENTER_ATTRIBUTE, OPTIONAL);
		element.addAttribute(ON_EXIT_ATTRIBUTE, OPTIONAL);
		element.addAttribute(HAS_TEXT_ATTRIBUTE, OPTIONAL);

		// set element's onEnter and onExit handlers
		element.setOnEnter("startElementElement"); //$NON-NLS-1$
		element.setOnExit("exitElementElement"); //$NON-NLS-1$

		// allow element to transition to self
		element.addTransition(element);

		// create attribute element and add as child of element
		ISchemaElement attribute = result.createElement(ATTRIBUTE_ELEMENT);
		element.addTransition(attribute);

		// set attribute's attributes
		attribute.addAttribute(NAME_ATTRIBUTE, REQUIRED);
		attribute.addAttribute(USAGE_ATTRIBUTE, OPTIONAL);

		// set attribute element's onEnter handler
		attribute.setOnEnter("startAttributeElement"); //$NON-NLS-1$

		// create sets element and add as child of root
		ISchemaElement sets = result.createElement(SETS_ELEMENT);
		root.addTransition(sets);

		// create element-set element and add as child of sets element
		ISchemaElement elementSet = result.createElement(ELEMENT_SET_ELEMENT);
		sets.addTransition(elementSet);

		// set elementSet's attributes
		elementSet.addAttribute("id", REQUIRED); //$NON-NLS-1$

		// set elementSet's onEnter and onExit handlers
		elementSet.setOnEnter("startElementSetElement"); //$NON-NLS-1$
		elementSet.setOnExit("exitElementSetElement"); //$NON-NLS-1$

		// add element as child of element-set
		elementSet.addTransition(element);

		// create use-element-set element and add as child of root, element, and element-set
		ISchemaElement useElementSet = result.createElement(USE_ELEMENT_SET_ELEMENT);
		root.addTransition(useElementSet);
		element.addTransition(useElementSet);
		elementSet.addTransition(useElementSet);

		// add useElementSet's attributes
		useElementSet.addAttribute(NAME_ATTRIBUTE, REQUIRED);

		// add useElementSet's onEnter handler
		useElementSet.setOnEnter("startUseElementSetElement"); //$NON-NLS-1$

		return result;
	}

	/**
	 * Finish processing the specified element
	 * 
	 * @param namespaceURI
	 * @param localName
	 * @param qualifiedName
	 */
	public void exitElementElement(String namespaceURI, String localName, String qualifiedName)
	{
		// restore parent element as the new current element
		this._currentElement = this._elementStack.pop();
	}

	/**
	 * Finish processing the specified element-set
	 * 
	 * @param namespaceURI
	 * @param localName
	 * @param qualifiedName
	 */
	public void exitElementSetElement(String namespaceURI, String localName, String qualifiedName)
	{
		// save set
		this._sets.put(this._currentSetId, this._currentElement);

		// clear name
		this._currentSetId = null;

		// restore parent element as the new current element
		this._currentElement = this._elementStack.pop();
	}

	/**
	 * Load an xml schema that describes and recognizes a specific xml format
	 * 
	 * @param filename
	 *            The name of the xml schema file to load
	 * @param handler
	 *            The handler to use for event callbacks
	 * @return A validating XML reader that will recognize and validate against the loaded schema
	 * @throws SchemaInitializationException
	 */
	public static Schema fromXML(String filename, Object handler) throws SchemaInitializationException
	{
		FileInputStream fi = null;
		Schema schema = null;

		try
		{
			fi = new FileInputStream(filename);

			schema = fromXML(fi, handler);
		}
		catch (FileNotFoundException e)
		{
			String msg = Messages.SchemaBuilder_File_Unlocatable + filename;
			SchemaInitializationException ie = new SchemaInitializationException(msg, e);

			throw ie;
		}
		finally
		{
			if (fi != null)
			{
				try
				{
					fi.close();
				}
				catch (IOException e)
				{
				}
			}
		}

		return schema;
	}

	/**
	 * Load an xml schema that describes and recognizes a specific xml format
	 * 
	 * @param in
	 *            The input stream of xml schema data
	 * @param handler
	 *            The handler to use for event callbacks
	 * @return A validating XML reader that will recognize and validate against the loaded schema
	 * @throws SchemaInitializationException
	 */
	public static Schema fromXML(InputStream in, Object handler) throws SchemaInitializationException
	{
		Schema result = new Schema(handler);

		SchemaBuilder builder = new SchemaBuilder();

		// setup selector schema and reset in case it has been used previously
		builder._schema = builder._versionSelectorSchema;
		builder._schema.reset();

		// create new schema and associate with resulting reader
		builder._newSchema = result;

		// build new schema from XML description
		try
		{
			builder.read(in);
		}
		catch (ParserConfigurationException e)
		{
			String msg = Messages.SchemaBuilder_SAX_Parser_Initialization_Error;
			SchemaInitializationException ie = new SchemaInitializationException(msg, e);

			throw ie;
		}
		catch (SAXException e)
		{
			String msg = Messages.SchemaBuilder_SAX_Parser_Error;
			SchemaInitializationException ie = new SchemaInitializationException(msg, e);

			throw ie;
		}
		catch (IOException e)
		{
			String msg = Messages.SchemaBuilder_IO_Error;
			SchemaInitializationException ie = new SchemaInitializationException(msg, e);

			throw ie;
		}

		// return results
		return result;
	}

	/**
	 * Process an &lt;attribute&gt;
	 * 
	 * @param namespaceURI
	 * @param localName
	 * @param qualifiedName
	 * @param attributes
	 */
	public void startAttributeElement(String namespaceURI, String localName, String qualifiedName, Attributes attributes)
	{
		String name = attributes.getValue(NAME_ATTRIBUTE);
		String usage = attributes.getValue(USAGE_ATTRIBUTE);

		this._currentElement.addAttribute(name, usage);
	}

	/**
	 * Process a &lt;child-element&gt;
	 * 
	 * @param namespaceURI
	 * @param localName
	 * @param qualifiedName
	 * @param attributes
	 */
	public void startChildElementElement(String namespaceURI, String localName, String qualifiedName,
			Attributes attributes)
	{
		// get target element's name
		String elementName = attributes.getValue(NAME_ATTRIBUTE);

		// create a new SchemaElement for our target element
		ISchemaElement element = this._newSchema.createElement(elementName);

		this._currentElement.addTransition(element);
	}

	/**
	 * startDocument handler
	 * 
	 * @throws SAXException
	 */
	public void startDocument() throws SAXException
	{
		super.startDocument();

		// reset the stack
		this._elementStack.clear();

		// set the root element as our current element
		this._currentElement = this._newSchema.getRootElement();
	}

	/**
	 * Start processing a element element
	 * 
	 * @param namespaceURI
	 * @param localName
	 * @param qualifiedName
	 * @param attributes
	 * @throws SAXException
	 */
	public void startElementElement(String namespaceURI, String localName, String qualifiedName, Attributes attributes)
			throws SAXException
	{
		// get target element's name and type
		String elementName = attributes.getValue(NAME_ATTRIBUTE);
		String elementType = attributes.getValue(TYPE_ATTRIBUTE);
		String onEnter = attributes.getValue(ON_ENTER_ATTRIBUTE);
		String onExit = attributes.getValue(ON_EXIT_ATTRIBUTE);
		String hasText = attributes.getValue(HAS_TEXT_ATTRIBUTE);
		String allowFreeformMarkup = attributes.getValue(ALLOW_FREEFORM_MARKUP_ATTRIBUTE);

		// create a new SchemaElement for our target element
		ISchemaElement element = this._newSchema.createElement(elementName);

		// tag as root node, if needed
		if (SCHEMA_1_0_NAMESPACE.equals(namespaceURI))
		{
			if (elementType != null && elementType.equals("root")) //$NON-NLS-1$
			{
				// add new target element as a transition from the current element
				this._currentElement.addTransition(element);
			}
		}
		else
		{
			// assuming 1.1

			// add new target element as a transition from the current element
			this._currentElement.addTransition(element);
		}

		// set onEnter, if defined
		if (onEnter != null && onEnter.length() > 0)
		{
			try
			{
				element.setOnEnter(onEnter);
			}
			catch (SecurityException e)
			{
				String message = Messages.SchemaBuilder_Unable_To_Get_OnEnter_Method + onEnter;

				throw new SAXException(message, e);
			}
			catch (NoSuchMethodException e)
			{
				String message = Messages.SchemaBuilder_Unable_To_Locate_OnEnter_Method + onEnter;

				throw new SAXException(message, e);
			}
		}

		// set onExit, if defined
		if (onExit != null && onExit.length() > 0)
		{
			try
			{
				element.setOnExit(onExit);
			}
			catch (SecurityException e)
			{
				String message = Messages.SchemaBuilder_Unable_To_Get_OnExit_Method + onExit;

				throw new SAXException(message, e);
			}
			catch (NoSuchMethodException e)
			{
				String message = Messages.SchemaBuilder_Unable_To_Locate_OnExit_Method + onExit;

				throw new SAXException(message, e);
			}
		}

		// set hasText, if defined
		if (hasText != null && hasText.length() > 0)
		{
			String lowerHasText = hasText.toLowerCase();
			boolean hasTextValue = (lowerHasText.equals("true") || lowerHasText.equals("yes")); //$NON-NLS-1$ //$NON-NLS-2$

			element.setHasText(hasTextValue);
		}

		// set allowFreeformMarkup, if defined
		if (allowFreeformMarkup != null && allowFreeformMarkup.length() > 0)
		{
			String lowerText = allowFreeformMarkup.toLowerCase();
			boolean allowFreeformMarkupValue = (lowerText.equals("true") || lowerText.equals("yes")); //$NON-NLS-1$ //$NON-NLS-2$

			element.setAllowFreeformMarkup(allowFreeformMarkupValue);
		}

		// save current element on the stack
		this._elementStack.push(this._currentElement);

		// set our new element as the new current element
		this._currentElement = element;
	}

	/**
	 * Start processing a element-set element
	 * 
	 * @param namespaceURI
	 * @param localName
	 * @param qualifiedName
	 * @param attributes
	 * @throws SAXException
	 */
	public void startElementSetElement(String namespaceURI, String localName, String qualifiedName,
			Attributes attributes) throws SAXException
	{
		// create a new SchemaElement for our target element
		ISchemaElement set = this._newSchema.createElement(localName, false);

		// get id
		String id = attributes.getValue("id"); //$NON-NLS-1$

		// save set name for later
		this._currentSetId = id;

		// save current element on the stack
		this._elementStack.push(this._currentElement);

		// set our new element as the new current element
		this._currentElement = set;
	}

	/**
	 * Start processing a schema element
	 * 
	 * @param namespaceURI
	 * @param localName
	 * @param qualifiedName
	 * @param attributes
	 * @throws SAXException
	 */
	public void startSchemaElement(String namespaceURI, String localName, String qualifiedName, Attributes attributes)
			throws SAXException
	{
		if (SCHEMA_1_0_NAMESPACE.equals(namespaceURI))
		{
			this._schema = this._schema10;
		}
		else if (SCHEMA_1_1_NAMESPACE.equals(namespaceURI))
		{
			this._schema = this._schema11;
		}
		else
		{
			String message = Messages.SchemaBuilder_Unknown_Schema_Namespace + namespaceURI;

			throw new SAXException(message);
		}

		try
		{
			this._schema.reset();
			this._schema.moveTo(namespaceURI, localName, qualifiedName, attributes);
		}
		catch (IllegalArgumentException e)
		{
			throw new SAXException(e);
		}
		catch (InvalidTransitionException e)
		{
			throw new SAXException(e);
		}
		catch (IllegalAccessException e)
		{
			throw new SAXException(e);
		}
		catch (InvocationTargetException e)
		{
			throw new SAXException(e);
		}
	}

	/**
	 * Start processing a schema element
	 * 
	 * @param namespaceURI
	 * @param localName
	 * @param qualifiedName
	 * @param attributes
	 * @throws SAXException
	 */
	public void startUseElementSetElement(String namespaceURI, String localName, String qualifiedName,
			Attributes attributes) throws SAXException
	{
		String name = attributes.getValue(NAME_ATTRIBUTE);
		String id = name.substring(1);

		this.addSetToElement(id, this._currentElement);
	}

	/**
	 * addSetToElement
	 * 
	 * @param id
	 *            The name of the element to add
	 * @param element
	 *            The element to which the set children will be added
	 */
	private void addSetToElement(String id, ISchemaElement element)
	{
		if (this._sets.containsKey(id))
		{
			ISchemaElement set = this._sets.get(id);
			ISchemaElement[] children = set.getTransitionElements();

			if (children != null)
			{
				for (ISchemaElement child : children)
				{
					this._currentElement.addTransition(child);
				}
			}
		}
		else
		{
			throw new IllegalArgumentException(Messages.SchemaBuilder_Set_ID_Not_Defined + id);
		}
	}
}
