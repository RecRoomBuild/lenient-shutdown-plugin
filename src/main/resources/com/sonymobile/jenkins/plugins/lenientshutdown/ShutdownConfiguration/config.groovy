/*
 *  The MIT License
 *
 *  Copyright (c) 2014 Sony Mobile Communications Inc. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
*/

f=namespace("lib/form")

f.section(title:_("Lenient Shutdown")) {
    f.entry(field: 'shutdownMessage',
            title:_("Shutdown message"),
            description:_("This message will be displayed in the header on all " +
                    "pages when lenient shutdown mode is activated")) {
        f.textbox()
    }
    f.entry(field: 'allowAllQueuedItems', title:_("Allow all queued items")) {
    	f.checkbox()
    }
    f.entry(field: 'allowAllDownstreamItems', title:_("Allow all downstream items")) {
        f.checkbox()
    }
    f.entry(field: 'allowAllowListedProjects', title:_("Allow allow-listed projects")) {
    	f.checkbox()
    }
    f.entry(field:'allowListedProjects',
    		title:_("Allow-listed projects"),
    		description:_("One entry per line.")) {
    	f.textarea(value:instance.allowListedProjectsText)
    }
}
