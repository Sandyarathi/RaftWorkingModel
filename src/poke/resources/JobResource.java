/*
 * copyright 2012, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.resources;

import poke.comm.App.Request;
import poke.server.resources.Resource;
import poke.comm.App.Payload;
import poke.comm.App.PokeStatus;
import poke.comm.App.JobDesc;
import poke.comm.App.JobStatus;
import poke.comm.App.NameValueSet;

public class JobResource implements Resource {

	@Override
	public Request process(Request request) {
		JobDesc data = request.getBody().getJobOp().getData();
		String nameSpace = data.getNameSpace();
		NameValueSet options = data.getOptions();
		
		Request.Builder requestBuilder = Request.newBuilder()
				.setHeader(request.getHeader());
		JobDesc.Builder jobDescBuilder = JobDesc.newBuilder();
		
		if (nameSpace.equals("sign_up")) {
			// sign up logic
		} else if (nameSpace.equals("sign_in")) {
			// sign in logic
		} else if (nameSpace.equals("listcourses")) {			
			jobDescBuilder.setOptions(NameValueSet.newBuilder()
					.setNodeType(options.getNodeType())
					.setName(options.getName())
					.setValue(options.getValue())					
					.addNode(NameValueSet.newBuilder()
							.setNodeType(NameValueSet.NodeType.VALUE)
							.setName("Java")
							.setValue("Basic Java Tutorial"))
					.addNode(NameValueSet.newBuilder()
							.setNodeType(NameValueSet.NodeType.VALUE)
							.setName("Python")
							.setValue("Basic Python Learning"))
					.addNode(NameValueSet.newBuilder()
							.setNodeType(NameValueSet.NodeType.VALUE)
							.setName("iOS")
							.setValue("Basic iOS Tutorial"))
					.addNode(NameValueSet.newBuilder()
							.setNodeType(NameValueSet.NodeType.VALUE)
							.setName("Android")
							.setValue("Basic Android Learning"))
					.addNode(NameValueSet.newBuilder()
							.setNodeType(NameValueSet.NodeType.VALUE)
							.setName("AngularJS Tutorial")
							.setValue("AngularJS Framework Walkthrough")));
		} else if (nameSpace.equals("getdescription")) {
			jobDescBuilder.setOptions(NameValueSet.newBuilder()
					.setNodeType(options.getNodeType())
					.setName(options.getName())
					.setValue("Basic " + options.getValue() + " Tutorial"));
		}
		
		jobDescBuilder.setNameSpace(nameSpace);						
		jobDescBuilder.setOwnerId(data.getOwnerId());
		jobDescBuilder.setJobId(data.getJobId());
		jobDescBuilder.setStatus(data.getStatus());
		
		Payload.Builder payloadBuilder = Payload.newBuilder();
		
		JobStatus.Builder jobStatusBuilder = JobStatus.newBuilder();
		jobStatusBuilder.setJobId(data.getJobId());
		jobStatusBuilder.setJobState(data.getStatus());
		jobStatusBuilder.setStatus(PokeStatus.SUCCESS);
		jobStatusBuilder.addData(jobDescBuilder);

		payloadBuilder.setJobStatus(jobStatusBuilder);	
		requestBuilder.setBody(payloadBuilder);
		return requestBuilder.build();
	}
}