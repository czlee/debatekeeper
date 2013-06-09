#!/usr/bin/env python

try:
    import lxml.etree as ET
except ImportError:
    import xml.etree.ElementTree as ET
import optparse

parser = optparse.OptionParser(usage="%prog original_file new_file")
_, args = parser.parse_args()

if len(args) != 2:
    parser.print_usage()
    exit(1)

old_filename = args[0]
new_filename = args[1]

root_original  = ET.parse(old_filename).getroot()
if not root_original.tag == "debateformat":
    print "The root element of the original file is not <debateformat>"
    exit(2)
if not root_original.get("schemaversion") in ["1.0", "1.1"]:
    print "The schema version of the original file is not 1.0 or 1.1"
    exit(2)

root_converted = ET.Element("debate-format")
root_converted.set("schema-version", "2.0")

def create_element_if_existent(parent, name, content):
    """Creates the element but only if there is something to put in it."""
    if content is None:
        return
    element = ET.SubElement(parent, name)
    if content is not None:
        element.text = str(content)
    return element

def set_attribute_if_existent(parent, key, value):
    """Sets the attribute but only if there is something to put in it."""
    if value is None:
        return
    parent.set(key, value)

# "name" -> <name>
name = root_original.get("name")
create_element_if_existent(root_converted, "name", name)

# <info> -> <info>
info_original  = root_original.find("info")
info_converted = ET.SubElement(root_converted, "info")

for region  in info_original.findall("region"):
    create_element_if_existent(info_converted, "region",  region.text)
for level   in info_original.findall("level"):
    create_element_if_existent(info_converted, "level",   level.text)
for used_at in info_original.findall("usedat"):
    create_element_if_existent(info_converted, "used-at", used_at.text)

description = info_original.find("desc")
create_element_if_existent(info_converted, "description", description.text)

# Resources, bells, periods and speech-types get complicated because of the
# restructuring, so we'll use classes and objects to keep track of them properly.
# Rather than write as we go, we'll build up a map of everything first, and then
# write everything in <period-types> and <speech-types> once we've got the map.
#
# More specifically, the restructuring we need to be wary of includes:
#  (a) periods have global scope now
#  (b) there are built-in periods that can be used without local file definition
#
# None of the following classes have defined constructors.  The "constructor"
# create_from_element() is defined as a static method for readability (it seems
# counter-intuitive to call it a "constructor" when its job is to deconstruct
# an Element object).  In theory, you could replace each create_from_element()
# with a normal constructor taking an Element object and it would function the
# same.



class Period(object):

    BUILT_IN_REFS = ["normal", "pois-allowed", "warning", "overtime"]

    # The purposes of the instances list are:
    # (1) to ensure global uniqueness, since in schema 2.0 periods must be globally
    #     unique, whereas in schema 1.0 they only had to be unique locally to the
    #     resource/speech type.
    # (2) to provide storage facilities so we can group all the <period-type> elements
    #     in the same place in the converted file.
    instances = list()

    @classmethod
    def add_to_instances(cls, period):
        assert isinstance(period, cls)

        # However, if it is built-in, then the converted reference should just
        # be the built-in equivalent, not the actual key.
        if period.matches_built_in:
            period.converted_ref = period._built_in_equivalent()

        else:
            existing_refs = [p.converted_ref for p in cls.instances] + cls.BUILT_IN_REFS
            converted_ref = period.original_ref # initialise

            suffix = 0
            while converted_ref in existing_refs:
                suffix += 1
                converted_ref = period.original_ref + str(suffix)

            period.converted_ref = converted_ref

        assert period.converted_ref is not None

        cls.instances.append(period)

    @classmethod
    def create_from_element(cls, element):
        assert element.tag == "period"
        self = cls()
        self.original_ref = element.get("ref")
        self.description = element.get("desc")
        self.bgcolor = element.get("bgcolor")
        self.pois_allowed = element.get("poisallowed")

        # This is used to ensure uniqueness for schema 2.0
        cls.add_to_instances(self)

        return self

    @property
    def pois_allowed_value(self):
        if self.pois_allowed == "true":
            return True
        elif self.pois_allowed == "false":
            return False
        elif self.pois_allowed is None:
            return False
        else:
            raise RuntimeError("Invalid poisallowed value: {0!r}".format(self.pois_allowed))

    @property
    def matches_built_in(self):
        return self._built_in_equivalent() is not None

    def _built_in_equivalent(self):
        """Returns the built-in period type name if this period's properties happen
        to match one of the built-in type, otherwise returns None."""
        if self.description is not None and self.description != "":
            return None
        if (self.bgcolor is None or self.bgcolor.endswith("000000") or self.bgcolor == "#stay") and self.pois_allowed_value == False:
            return "normal"
        if self.bgcolor == "#7700ff00" and self.pois_allowed_value == True:
            return "pois-allowed"
        if self.bgcolor == "#77ffcc00" and self.pois_allowed_value == False:
            return "warning"
        if self.bgcolor == "#77ff0000" and self.pois_allowed_value == False:
            return "overtime"
        return None

    def get_converted_element(self):
        element = ET.Element("period-type")
        set_attribute_if_existent(element, "ref", self.converted_ref)
        set_attribute_if_existent(element, "pois-allowed", self.pois_allowed)
        name = raw_input("Please give a human-readable name for the period '{0}': ".format(self.converted_ref))
        create_element_if_existent(element, "name", name)
        create_element_if_existent(element, "display", self.description)
        create_element_if_existent(element, "default-bgcolor", self.bgcolor)
        return element

class Bell(object):

    def __repr__(self):
        return "<Bell object (time {0}) at {1:#x}>".format(self.time, hash(self))

    @classmethod
    def create_from_element(cls, element):
        assert element.tag == "bell"
        self = cls()
        self.time = element.get("time")
        self.number = element.get("number")
        self.pause = element.get("pauseonbell")
        self.next_period_ref = element.get("nextperiod")
        return self

    def set_next_period(self, period):
        """Sets the next period of the element.
        The caller must set the appropriate period as found from self.next_period_ref.
        This is not supported by this class as is requires knowledge of the context
        outside the bell."""
        assert isinstance(period, Period)
        self.next_period = period

    def get_converted_element(self):
        element = ET.Element("bell")
        set_attribute_if_existent(element, "time", self.time)
        set_attribute_if_existent(element, "number", self.number)
        set_attribute_if_existent(element, "pause-on-bell", self.pause)

        if self.next_period_ref is not None:
            try:
                new_next_period_ref = self.next_period.converted_ref
            except AttributeError:
                raise RuntimeError("set_next_period() wasn't called on " + repr(self))
            element.set("next-period", new_next_period_ref)

        return element

class SpeechElementsContainer(object):

    @classmethod
    def create_from_element(cls, element):
        self = cls()
        self._populate_from_element(element)
        return self

    def _populate_from_element(self, element):
        self.periods = list()
        self.bells = list()

        for period_element in element.findall("period"):
            period = Period.create_from_element(period_element)
            self.periods.append(period)

        for bell_element in element.findall("bell"):
            bell = Bell.create_from_element(bell_element)
            self.bells.append(bell)

    def find_period(self, ref):
        """Returns the Period object with reference 'ref'."""
        if ref is None:
            return None
        for period in self.periods:
            if period.original_ref == ref:
                return period
        raise ValueError("No period with {0!r} found in {1!r}".format(ref, self))

    def get_converted_element(self):
        raise NotImplementedErrorfirst_

class Resource(SpeechElementsContainer):

    @classmethod
    def create_from_element(cls, element):
        assert element.tag == "resource"
        self = cls()
        self._populate_from_element(element)
        self.ref = element.get("ref")
        return self

class ControlledTime(SpeechElementsContainer):

    @classmethod
    def create_from_element(cls, element):
        self = cls()
        self._populate_from_element(element)
        return self

    def _populate_from_element(self, element):
        SpeechElementsContainer._populate_from_element(self, element)
        self.length = element.get("length")
        self.first_period_ref = element.get("firstperiod")

    def find_and_set_periods(self):
        """Call this after all periods have been added.
        Sets the first period, and next periods of all bells."""
        self.first_period = self.find_period(self.first_period_ref)

        for bell in self.bells:
            next_period = self.find_period(bell.next_period_ref)
            if next_period is not None:
                bell.set_next_period(next_period)

    def _populate_converted_element(self, element):
        """Populates the element given to it with the length, first period ref,
        periods and bells."""
        set_attribute_if_existent(element, "length", self.length)

        if self.first_period is not None:
            new_first_period_ref = self.first_period.converted_ref
            element.set("first-period", new_first_period_ref)

        for bell in self.bells:
            bell_element = bell.get_converted_element()
            element.append(bell_element)

        return element

class SpeechType(ControlledTime):

    def __repr__(self):
        return "<SpeechType object '{0}' at {1:#x}>".format(self.ref, hash(self))

    @classmethod
    def create_from_element(cls, element):
        assert element.tag == "speechtype"
        self = cls()
        self.ref = element.get("ref")

        self._populate_from_element(element)

        self.resource_refs = list()

        for include_element in element.findall("include"):
            resource_ref = include_element.get("resource")
            self.resource_refs.append(resource_ref)

        return self

    def add_resource(self, resource):
        """Adds a resource.
        The caller must add the appropriate resources as found from self.resource_refs.
        This is not supported by this class as is requires knowledge of the context
        outside the speech type."""
        assert isinstance(resource, Resource)
        self.periods.extend(resource.periods)
        self.bells.extend(resource.bells)

    def get_converted_element(self):
        element = ET.Element("speech-type")
        set_attribute_if_existent(element, "ref", self.ref)
        self._populate_converted_element(element)
        return element

class PrepTimeControlled(ControlledTime):

    @classmethod
    def create_from_element(cls, element):
        assert element.tag == "preptime-controlled"
        self = cls()
        self._populate_from_element(element)
        self.find_and_set_periods()
        return self

    def get_converted_element(self):
        element = ET.Element("prep-time-controlled")
        self._populate_converted_element(element)
        return element

class PrepTimeSimple(object):

    @classmethod
    def create_from_element(cls, element):
        assert element.tag == "preptime"
        self = cls()
        self.length = element.get("length")
        return self

    def get_converted_element(self):
        element = ET.Element("prep-time")
        set_attribute_if_existent(element, "length", self.length)
        return element

# Build the map of the original file

# <resource> -> store somewhere useful
resources = dict()
resource_elements = root_original.findall("resource")
for element in resource_elements:
    resource = Resource.create_from_element(element)
    resources[resource.ref] = resource

# <preptime> or <preptime-controlled>
# (Note: In schema 1.0, preptimes aren't allowed to have <include resource> elements.)
preptime_element_simple = root_original.find("preptime")
preptime_element_controlled = root_original.find("preptime-controlled")
if preptime_element_simple is not None:
    preptime = PrepTimeSimple.create_from_element(preptime_element_simple)
elif preptime_element_controlled is not None:
    preptime = PrepTimeControlled.create_from_element(preptime_element_controlled)
else:
    preptime = None

# <speechtype>
speechtypes = list()
speechtype_elements = root_original.findall("speechtype")
for element in speechtype_elements:
    speechtype = SpeechType.create_from_element(element)

    if "#all" in resources:
        speechtype.add_resource(resources["#all"])

    # We have to add resources ourselves here, where we know the context
    for ref in speechtype.resource_refs:
        try:
            resource = resources[ref]
        except KeyError:
            raise RuntimeError("Could not find resource {0!r}".format(ref))
        speechtype.add_resource(resource)

    speechtype.find_and_set_periods()

    speechtypes.append(speechtype)

# Add the appropriate elements to the converted file

# <period-types>
periods_to_write = [p for p in Period.instances if not p.matches_built_in]
if periods_to_write:
    period_types = ET.SubElement(root_converted, "period-types")
    for period in periods_to_write:
        period_types.append(period.get_converted_element())

# <prep-time> or <prep-time-controlled>
if preptime is not None:
    root_converted.append(preptime.get_converted_element())

# <speech-types>
speech_types = ET.SubElement(root_converted, "speech-types")
for speechtype in speechtypes:
    speech_types.append(speechtype.get_converted_element())

# <speeches>
speeches_converted = ET.SubElement(root_converted, "speeches")
speeches_original = root_original.find("speeches")
speech_elements = speeches_original.findall("speech")
for element_original in speech_elements:
    speech_converted = ET.SubElement(speeches_converted, "speech")
    name = element_original.get("name")
    format = element_original.get("type")
    set_attribute_if_existent(speech_converted, "type", format)
    create_element_if_existent(speech_converted, "name", name)

f = open(new_filename, "w")
tree = ET.ElementTree(root_converted)
try:
    tree.write(f, pretty_print=True, xml_declaration=True, encoding="UTF-8")
except TypeError:
    tree.write(f, encoding="UTF-8")
f.close()