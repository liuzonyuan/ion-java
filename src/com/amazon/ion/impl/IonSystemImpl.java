/*
 * Copyright (c) 2007 Amazon.com, Inc.  All rights reserved.
 */

package com.amazon.ion.impl;

import com.amazon.ion.ContainedValueException;
import com.amazon.ion.IonBlob;
import com.amazon.ion.IonBool;
import com.amazon.ion.IonCatalog;
import com.amazon.ion.IonClob;
import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonDecimal;
import com.amazon.ion.IonException;
import com.amazon.ion.IonFloat;
import com.amazon.ion.IonInt;
import com.amazon.ion.IonList;
import com.amazon.ion.IonLoader;
import com.amazon.ion.IonNull;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonSexp;
import com.amazon.ion.IonString;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSymbol;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonTimestamp;
import com.amazon.ion.IonValue;
import com.amazon.ion.LocalSymbolTable;
import com.amazon.ion.StaticSymbolTable;
import com.amazon.ion.SystemSymbolTable;
import com.amazon.ion.UnsupportedSystemVersionException;
import com.amazon.ion.impl.IonBinary.BufferManager;
import com.amazon.ion.system.SimpleCatalog;
import com.amazon.ion.util.Printer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * The standard, public implementation of Ion.
 */
public class IonSystemImpl
    implements IonSystem
{
    private SystemSymbolTableImpl mySystemSymbols = new SystemSymbolTableImpl();
    private IonCatalog myCatalog;
    private IonLoader  myLoader = new LoaderImpl(this);


    public IonSystemImpl()
    {
        myCatalog = new SimpleCatalog();
    }

    public IonSystemImpl(IonCatalog catalog)
    {
        myCatalog = catalog;
    }


    public SystemSymbolTableImpl getSystemSymbolTable()
    {
        return mySystemSymbols;
    }

    public SystemSymbolTable getSystemSymbolTable(String systemId)
        throws UnsupportedSystemVersionException
    {
        if (systemId.equals(SystemSymbolTable.ION_1_0))
        {
            return mySystemSymbols;
        }

        throw new UnsupportedSystemVersionException(systemId);
    }


    public synchronized IonCatalog getCatalog()
    {
        return myCatalog;
    }


    public synchronized void setCatalog(IonCatalog catalog)
    {
        if (catalog == null) throw new NullPointerException();
        myCatalog = catalog;
    }


    public LocalSymbolTable newLocalSymbolTable()
    {
        return new LocalSymbolTableImpl(mySystemSymbols);
    }


    public LocalSymbolTable newLocalSymbolTable(SystemSymbolTable systemSymbols)
    {
        return new LocalSymbolTableImpl(systemSymbols);
    }


    public StaticSymbolTable newStaticSymbolTable(IonStruct symbolTable)
    {
        return new StaticSymbolTableImpl(this, symbolTable);
    }


    public IonDatagram newDatagram(IonValue initialChild)
        throws ContainedValueException
    {
        if (initialChild.getContainer() != null)
        {
            initialChild = clone(initialChild);
        }

        IonDatagramImpl datagram = new IonDatagramImpl(this);

        // This will fail if initialChild instanceof IonDatagram:
        datagram.add(initialChild);

        return datagram;
    }


    public IonLoader newLoader()
    {
        return new LoaderImpl(this);
    }

    public synchronized IonLoader getLoader()
    {
        return myLoader;
    }

    public synchronized void setLoader(IonLoader loader)
    {
        if (loader == null) throw new NullPointerException();
        myLoader = loader;
    }



    //=========================================================================
    // IonReader creation


    public IonReader newReader(Reader reader)
    {
        return new UserReader(this, this.newLocalSymbolTable(), reader);
    }

    /**
     *  @deprecated Use {@link #newReader(Reader)}.
     */
    @Deprecated
    public IonReader newTextReader(Reader reader)
    {
        return newReader(reader);
    }


    public IonReader newReader(String ionText)
    {
        return new UserReader(this,
                              this.newLocalSymbolTable(),
                              new StringReader(ionText));
    }

    /**
     *  @deprecated Use {@link #newReader(String)}.
     */
    @Deprecated
    public IonReader newTextReader(String ionText)
    {
        return newReader(ionText);
    }


    public IonReader newReader(byte[] ionData)
    {
        SystemReader systemReader = newSystemReader(ionData);
        return new UserReader(systemReader);
    }


    /**
     * Creates a new reader, wrapping an array of text or binary data.
     *
     * @param ionData may be (UTF-8) text or binary.
     * <em>This method assumes ownership of the array</em> and may modify it at
     * will.
     *
     * @throws NullPointerException if <code>ionData</code> is null.
     */
    public SystemReader newSystemReader(byte[] ionData)
    {
        boolean isbinary =
            IonBinary.startsWithBinaryVersionMarker(ionData);

        SystemReader sysReader;
        if (isbinary) {
            sysReader = newBinarySystemReader(ionData);
        }
        else {
            sysReader = newTextSystemReader(ionData);
        }

        return sysReader;
    }


    /**
     * Creates a new reader, wrapping an array of binary data.
     *
     * @param ionBinary must be Ion binary data, not text.
     * <em>This method assumes ownership of the array</em> and may modify it at
     * will.
     *
     * @throws NullPointerException if <code>ionBinary</code> is null.
     */
    private SystemReader newBinarySystemReader(byte[] ionBinary)
    {
        BlockedBuffer bb = new BlockedBuffer(ionBinary);
        BufferManager buffer = new BufferManager(bb);
        return new SystemReader(this, buffer);
    }


    /**
     * Creates a new reader, wrapping bytes holding UTF-8 text.
     *
     * @param ionText must be UTF-8 encoded Ion text data, not binary.
     * <em>This method assumes ownership of the array</em> and may modify it at
     * will.
     *
     * @throws NullPointerException if <code>ionText</code> is null.
     */
    private SystemReader newTextSystemReader(byte[] ionText)
    {
        ByteArrayInputStream stream = new ByteArrayInputStream(ionText);
        Reader reader;
        try {
            reader = new InputStreamReader(stream, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new IonException(e);
        }

        return new SystemReader(this, getCatalog(), reader);
    }


    public SystemReader newBinarySystemReader(InputStream ionBinary)
        throws IOException
    {
        BlockedBuffer bb = new BlockedBuffer();
        BufferManager buffer = new BufferManager(bb);
        IonBinary.Writer writer = buffer.writer();
        try {
            writer.write(ionBinary);
        }
        catch (IOException e) {
            throw new IonException(e);
        }
        finally {
            ionBinary.close();
        }

        return new SystemReader(this, buffer);
    }

    //-------------------------------------------------------------------------
    // System Elements

    /**
     * TODO this doesn't recognize general $ion_X_Y (and it should).
     */
    private final boolean valueIsSystemId(IonValue value)
    {
        if (value instanceof IonSymbol && ! value.isNullValue())
        {
            IonSymbol symbol = (IonSymbol) value;
            int sid = symbol.intValue();
            if (sid > 0)
            {
                return sid == SystemSymbolTableImpl.ION_1_0_SID;
            }

            return SystemSymbolTable.ION_1_0.equals(symbol.stringValue());
        }
        return false;
    }

    public final boolean valueIsStaticSymbolTable(IonValue value)
    {
        return (value instanceof IonStruct
                && value.hasTypeAnnotation(SystemSymbolTable.ION_SYMBOL_TABLE));
    }

    public final LocalSymbolTable handleLocalSymbolTable(IonCatalog catalog,
                                                         IonValue value)
    {
        // This assumes that we only are handling 1_0

        LocalSymbolTable symtab = null;

        if (value instanceof IonStruct)
        {
            if (value.hasTypeAnnotation(SystemSymbolTable.ION_1_0))
            {
                symtab = new LocalSymbolTableImpl(this, catalog,
                                                  (IonStruct) value,
                                                  mySystemSymbols);
            }
        }
        else if (valueIsSystemId(value))
        {
            symtab = new LocalSymbolTableImpl(mySystemSymbols);
        }

        return symtab;

    }


    //-------------------------------------------------------------------------
    // DOM creation

    private IonValue singleValue(IonReader reader)
    {
        try
        {
            if (reader.hasNext())
            {
                IonValue value = reader.next();
                if (! reader.hasNext())
                {
                    return value;
                }
            }
        }
        finally
        {
            reader.close();
        }

        throw new IonException("not a single value");
    }

    public IonValue singleValue(String ionText)
    {
        IonReader reader = newTextReader(ionText);
        return singleValue(reader);
    }

    public IonValue singleValue(byte[] ionData)
    {
        IonReader reader = newReader(ionData);
        return singleValue(reader);
    }

    /**
     * @deprecated Use {@link #newNullBlob()} instead
     */
    @Deprecated
    public IonBlob newBlob()
    {
        return newNullBlob();
    }


    public IonBlob newNullBlob()
    {
        return new IonBlobImpl();
    }


    /**
     * @deprecated Use {@link #newNullBool()} instead
     */
    @Deprecated
    public IonBool newBool()
    {
        return newNullBool();
    }


    public IonBool newNullBool()
    {
        return new IonBoolImpl();
    }

    public IonBool newBool(boolean value)
    {
        IonBool result = new IonBoolImpl();
        result.setValue(value);
        return result;
    }


    /**
     * @deprecated Use {@link #newNullClob()} instead
     */
    @Deprecated
    public IonClob newClob()
    {
        return newNullClob();
    }


    public IonClob newNullClob()
    {
        return new IonClobImpl();
    }


    /**
     * @deprecated Use {@link #newNullDecimal()} instead
     */
    @Deprecated
    public IonDecimal newDecimal()
    {
        return newNullDecimal();
    }


    public IonDecimal newNullDecimal()
    {
        return new IonDecimalImpl();
    }


    /**
     * @deprecated Use {@link #newNullFloat()} instead
     */
    @Deprecated
    public IonFloat newFloat()
    {
        return newNullFloat();
    }


    public IonFloat newNullFloat()
    {
        return new IonFloatImpl();
    }


    /**
     * @deprecated Use {@link #newNullInt()} instead
     */
    @Deprecated
    public IonInt newInt()
    {
        return newNullInt();
    }


    public IonInt newNullInt()
    {
        return new IonIntImpl();
    }

    public IonInt newInt(int content)
    {
        IonInt result = new IonIntImpl();
        result.setValue(content);
        return result;
    }

    public IonInt newInt(long content)
    {
        IonInt result = new IonIntImpl();
        result.setValue(content);
        return result;
    }

    public IonInt newInt(Number content)
    {
        IonInt result = new IonIntImpl();
        result.setValue(content);
        return result;
    }


    /**
     * @deprecated Use {@link #newNullList()} instead
     */
    @Deprecated
    public IonList newList()
    {
        return newNullList();
    }


    public IonList newNullList()
    {
        return new IonListImpl();
    }

    public IonList newEmptyList()
    {
        return new IonListImpl(false);
    }

    public IonList newList(Collection<? extends IonValue> elements)
        throws ContainedValueException, NullPointerException
    {
        return new IonListImpl(elements);
    }

    public <T extends IonValue> IonList newList(T... elements)
        throws ContainedValueException, NullPointerException
    {
        List<T> e = (elements == null ? null : Arrays.asList(elements));
        return new IonListImpl(e);
    }

    public IonList newList(int[] elements)
    {
        ArrayList<IonInt> e = newInts(elements);
        return newList(e);
    }

    public IonList newList(long[] elements)
    {
        ArrayList<IonInt> e = newInts(elements);
        return newList(e);
    }


    public IonNull newNull()
    {
        return new IonNullImpl();
    }


    /**
     * @deprecated Use {@link #newNullSexp()} instead
     */
    @Deprecated
    public IonSexp newSexp()
    {
        return newNullSexp();
    }


    public IonSexp newNullSexp()
    {
        return new IonSexpImpl();
    }

    public IonSexp newEmptySexp()
    {
        return new IonSexpImpl(false);
    }

    public IonSexp newSexp(Collection<? extends IonValue> elements)
        throws ContainedValueException, NullPointerException
    {
        return new IonSexpImpl(elements);
    }

    public <T extends IonValue> IonSexp newSexp(T... elements)
        throws ContainedValueException, NullPointerException
    {
        List<T> e = (elements == null ? null : Arrays.asList(elements));
        return new IonSexpImpl(e);
    }

    public IonSexp newSexp(int[] elements)
    {
        ArrayList<IonInt> e = newInts(elements);
        return newSexp(e);
    }

    public IonSexp newSexp(long[] elements)
    {
        ArrayList<IonInt> e = newInts(elements);
        return newSexp(e);
    }


    /**
     * @deprecated Use {@link #newNullString()} instead
     */
    @Deprecated
    public IonString newString()
    {
        return newNullString();
    }


    public IonString newNullString()
    {
        return new IonStringImpl();
    }

    public IonString newString(String content)
    {
        IonString result = new IonStringImpl();
        result.setValue(content);
        return result;
    }


    /**
     * @deprecated Use {@link #newNullStruct()} instead
     */
    @Deprecated
    public IonStruct newStruct()
    {
        return newNullStruct();
    }


    public IonStruct newNullStruct()
    {
        return new IonStructImpl();
    }

    public IonStruct newEmptyStruct()
    {
        IonStruct result = new IonStructImpl();
        result.clear();
        return result;
    }


    /**
     * @deprecated Use {@link #newNullSymbol()} instead
     */
    @Deprecated
    public IonSymbol newSymbol()
    {
        return newNullSymbol();
    }


    public IonSymbol newNullSymbol()
    {
        return new IonSymbolImpl();
    }

    public IonSymbol newSymbol(String name)
    {
        return new IonSymbolImpl(name);
    }

    /**
     * @deprecated Use {@link #newNullTimestamp()} instead
     */
    @Deprecated
    public IonTimestamp newTimestamp()
    {
        return newNullTimestamp();
    }


    public IonTimestamp newNullTimestamp()
    {
        return new IonTimestampImpl();
    }

    public IonTimestamp newUtcTimestampFromMillis(long millis)
    {
        IonTimestamp result = new IonTimestampImpl();
        result.setMillisUtc(millis);
        return result;
    }

    public IonTimestamp newUtcTimestamp(Date value)
    {
        IonTimestamp result = new IonTimestampImpl();
        if (value != null)
        {
            result.setMillisUtc(value.getTime());
        }
        return result;
    }

    public IonTimestamp newCurrentUtcTimestamp()
    {
        IonTimestamp result = new IonTimestampImpl();
        result.setCurrentTimeUtc();
        return result;
    }


    @SuppressWarnings("unchecked")
    public <T extends IonValue> T clone(T value)
    {
        if (value instanceof IonDatagram)
        {
            byte[] data = ((IonDatagram)value).toBytes();

            // TODO This can probably be optimized further.
            return (T) new IonDatagramImpl(this, data);
        }

        StringBuilder buffer = new StringBuilder();
        Printer printer = new Printer();
        try
        {
            printer.print(value, buffer);
        }
        catch (IOException e)
        {
            throw new IonException(e);
        }

        String text = buffer.toString();
        return (T) singleValue(text);
    }


    //=========================================================================
    // Helpers

    private ArrayList<IonInt> newInts(int[] elements)
    {
        ArrayList<IonInt> e = null;

        if (elements != null)
        {
            e = new ArrayList<IonInt>(elements.length);
            for (int i = 0; i < elements.length; i++)
            {
                int value = elements[i];
                e.add(newInt(value));
            }
        }

        return e;
    }

    private ArrayList<IonInt> newInts(long[] elements)
    {
        ArrayList<IonInt> e = null;

        if (elements != null)
        {
            e = new ArrayList<IonInt>(elements.length);
            for (int i = 0; i < elements.length; i++)
            {
                long value = elements[i];
                e.add(newInt(value));
            }
        }

        return e;
    }
}