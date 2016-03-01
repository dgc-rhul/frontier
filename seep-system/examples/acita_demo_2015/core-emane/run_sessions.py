#!/usr/bin/python

import sys,os,time,re,argparse,math,shutil,subprocess,socket


from core import pycore
from core.api import coreapi
from core.constants import *
from core.mobility import BasicRangeModel
from core.mobility import Ns2ScriptedMobility 
from core.emane.ieee80211abg import EmaneIeee80211abgModel
from core.emane.emane import EmaneGlobalModel
#from core.misc.xmlutils import savesessionxml
from core.misc.xmlsession import savesessionxml
from core.misc import ipaddr

script_dir = os.path.dirname(os.path.realpath(__file__))
#script_dir = '%s/dev/seep-ita/seep-system/examples/acita_demo_2015/core-emane'%os.environ['HOME']

print 'Appending script_dir to path'
sys.path.append(script_dir)
from util import chmod_dir,pybool_to_javastr
from gen_mobility_trace import gen_trace
from gen_fixed_routes import create_static_routes

#repo_dir = '%s/../../../..'
#svc_dir='/data/dev/seep-github/seep-system/examples/acita_demo_2015/core-emane/vldb/myservices'
svc_dir='%s/vldb/myservices'%script_dir
#conf_dir='/data/dev/seep-github/seep-system/examples/acita_demo_2015/core-emane/vldb/config'
conf_dir='%s/vldb/config'%script_dir
seep_jar = "seep-system-0.0.1-SNAPSHOT.jar"
mobility_params = [('file','%s/rwpt.ns_movements'%conf_dir),('refresh_ms',1000),
        ('loop',1),('autostart',1.0),('map',''),('script_start',''),('script_pause',''),('script_stop','')]


datacollect_template = '''#!/bin/bash
# session hook script; write commands here to execute on the host at the
# specified state


#echo "`hostname`:`pwd`" > /tmp/datacollect.log
#if [ -z "$SEEP_GITHUB_DIR" ]; then
#	echo "SEEP_GITHUB_DIR not set." >> /tmp/datacollect.log
#	SEEP_GITHUB_DIR=/data/dev/seep-github
#fi

#scriptDir=$SEEP_GITHUB_DIR/seep-system/examples/acita_demo_2015/core-emane
scriptDir=%s
timeStr=%s
k=%dk
mob=%.2fm
session=%ds
#resultsDir=$scriptDir/log/$timeStr
resultsDir=$scriptDir/log/$timeStr/$k/$mob/$session

expDir=$(pwd)

echo $expDir >> /tmp/datacollect.log
echo $scriptDir >> /tmp/datacollect.log
echo $timeStr >> /tmp/datacollect.log
echo $resultsDir >> /tmp/datacollect.log

mkdir -p $resultsDir

# Copy all log files to results dir
for d in n*.conf 
do
	cp $d/log1/*.log $resultsDir	
    if [ -e "$d/log2" ]
    then
        cp $d/log2/*.log $resultsDir	
    fi
	cp $d/mappingRecordOut.txt $resultsDir	
    mkdir $resultsDir/positions
	cp $d/*.xyz $resultsDir/positions	
	cp $d/mappingRecordOut.txt $scriptDir/log/$timeStr/session${session}MappingRecord.txt

    #Copy any emane stats
    if [ -d emane-stats ];
    then
        mkdir -p $resultsDir/emane-tables
        cp $d/emane-tables*.txt $resultsDir/emane-tables
    fi
done

#Copy mobility params if they exist
cp r_waypoints.params $resultsDir

#Copy dstat trace if exists 
cp dstat/*.csv $resultsDir

#Copy any emane stats
if [ -d emane-stats ];
then
	mkdir -p $resultsDir/emane-stats
	cp emane-stats/*.txt $resultsDir/emane-stats
fi

cd $scriptDir
#./gen_core_results.py --expDir log/$timeStr 
./gen_core_results.py --expDir $resultsDir
#./move_analysis.py --nodes 10 --expDir $resultsDir/positions
chmod -R go+rw $resultsDir
cd $expDir
'''
def run_sessions(time_str, k, mob, sessions, params):
    for session in sessions:
        print '*** Running session %d ***'%session
        run_session(time_str, k, mob, session, params)

def run_session(time_str, k, mob, exp_session, params):

    print 'params=',params

    distributed = bool(params['slave'])
    verbose = params['verbose'] 

    try:
        session_cfg = {'custom_services_dir':svc_dir, 'emane_log_level':'3',
                'verbose':"true" if verbose else "False"} 
        if params['preserve']: session_cfg['preservedir'] = '1' 
        if distributed: 
            slave = params['slave']
            slaveip = socket.gethostbyname(slave)
            session_cfg['controlnet'] = "%s:172.16.1.0/24 %s:172.16.2.0/24"%(socket.gethostname(), slave)
            session_cfg['controlnet1'] = "%s:172.17.1.0/24 %s:172.17.2.0/24"%(socket.gethostname(), slave)
            session_cfg['controlnetif1'] = "eth4"
        else: 
            session_cfg['controlnet'] = "172.16.1.0/24"

        session = pycore.Session(cfg=session_cfg, persistent=True)

        session.master=True
        session.location.setrefgeo(47.5791667,-122.132322,2.00000)
        session.location.refscale = 100.0
        session.metadata.additem("canvas c1", "{name {Canvas1}} {wallpaper-style {upperleft}} {wallpaper {/home/dan/dev/seep-ita/seep-system/examples/acita_demo_2015/core-emane/vldb/config/sample1-bg-large.png}} {size {3000 3000}}")

        if distributed: 
            conf_msg = raw_emane_global_conf_msg(1)
            session.broker.handlerawmsg(conf_msg)
            session.confobj("emane", session, to_msg(conf_msg))

            remote_configure(session, slave, slaveip)
            session.broker.handlerawmsg(location_conf_msg(session.location))

        if params['sinkDisplay']:
            sink_display = start_query_sink_display("sinkDisplay.log", session.sessiondir, params)
            print 'Started query sink display ', sink_display 

        params['repoDir'] = get_repo_dir()
        #This is so broken, should find a better way...
        write_replication_factor(k, session.sessiondir)
        write_chain_length(params['h'], session.sessiondir)
        write_query_type(params['query'], session.sessiondir)
        write_extra_params(params, session.sessiondir)
        write_session_params(params, session.sessiondir)

        copy_seep_jar(session.sessiondir)
        trace_file = None
        if mob > 0.0:
            trace_params = dict(params)
            trace_params['h'] = mob + 1.0
            trace_params['l'] = mob - 1.0
            trace_params['o'] = mob
            trace_file = gen_trace(session.sessiondir, exp_session, trace_params)
            print 'Trace file=',trace_file


        model = params.get('model')
        #tx_range = 499 #TODO: How to apply to emane?
        #tx_range = 399 #TODO: How to apply to emane?
        #tx_range = 599 #TODO: How to apply to emane?
        #tx_range = 525 #TODO: How to apply to emane?
        #tx_range = 1250 #TODO: How to apply to emane?
        #tx_range = 250 #TODO: How to apply to emane?
        #tx_range = 300 #TODO: How to apply to emane?
        #tx_range = 450 #TODO: How to apply to emane?
        #tx_range = 750 #TODO: How to apply to emane?
        #tx_range = 410 #TODO: How to apply to emane?
        tx_range = 410 #TODO: How to apply to emane?
        #tx_range = 260 #TODO: How to apply to emane?
        print 'Model=', model
        if model == "Emane":
            # Gives ping range of ~915m with 1:1 pixels to m and default 802.11
            # settings (2ray).
            session.cfg['emane_models'] = "RfPipe, Ieee80211abg, Bypass"
            session.emane.loadmodels()

            #prefix = ipaddr.IPv4Prefix("10.0.0.0/32")
            #tmp.newnetif(net, ["%s/%s" % (prefix.addr(i), prefix.prefixlen)])
            # set increasing Z coordinates
            wlan1 = session.addobj(cls = pycore.nodes.EmaneNode, name = "wlan1", objid=1, verbose=verbose)
            wlan1.setposition(x=80,y=50)
            if distributed: session.broker.handlerawmsg(wlan1.tonodemsg(flags=coreapi.CORE_API_ADD_FLAG|coreapi.CORE_API_STR_FLAG))
            """
            TODO: Might need to add this back!
            for servername in session.broker.getserverlist():
                session.broker.addnodemap(servername, wlan1.objid)
            """
            #msg = session.emane.emane_config.toconfmsg(coreapi.CORE_API_CONF_MSG, wlan1, todo, values)
            #session.emane.configure(session, msg)
            names = EmaneIeee80211abgModel.getnames()
            values = list(EmaneIeee80211abgModel.getdefaultvalues())
            print 'Emane Model default names: %s'%(str(names))
            print 'Emane Model default values: %s'%(str(values))
            # TODO: change any of the EMANE 802.11 parameter values here
            values[ names.index('mode') ] = '3'
            #values[ names.index('rtsthreshold') ] = '1024'
            values[ names.index('retrylimit') ] = '0:7'
            #values[ names.index('retrylimit') ] = '0:3'
            #values[ names.index('cwmin') ] = '0:16'
            #values[ names.index('cwmax') ] = '0:2048'
            values[ names.index('propagationmodel') ] = '2ray'
            #values[ names.index('propagationmodel') ] = 'freespace'
            #values[ names.index('pathlossmode') ] = '2ray'
            #values[ names.index('multicastrate') ] = '12'
            values[ names.index('multicastrate') ] = '4'
            #values[ names.index('multicastrate') ] = '1'
            #values[ names.index('unicastrate') ] = '12'
            #values[ names.index('distance') ] = '500'
            values[ names.index('unicastrate') ] = '4'
            values[ names.index('txpower') ] = '-10.0'
            #values[ names.index('txpower') ] = '-1.0'
            values[ names.index('flowcontrolenable') ] = 'on'
            #values[ names.index('flowcontrolenable') ] = 'off'
            values[ names.index('flowcontroltokens') ] = '10'
            print 'Emane Model overridden values: %s'%(str(list(values)))
            if distributed:
                typeflags = coreapi.CONF_TYPE_FLAGS_UPDATE
                msg = EmaneIeee80211abgModel.toconfmsg(flags=0, nodenum=wlan1.objid,
		     typeflags=typeflags, values=values)
                session.broker.handlerawmsg(msg)

            session.emane.setconfig(wlan1.objid, EmaneIeee80211abgModel._name, values)

            if distributed:
                #No idea why I need to call this again here with a None node but seems I have to. 
                conf_msg = raw_emane_global_conf_msg(None)
                session.broker.handlerawmsg(conf_msg)
                session.confobj("emane", session, to_msg(conf_msg))

        elif model == "Basic" and not distributed:
            wlan1 = session.addobj(cls = pycore.nodes.WlanNode, name="wlan1",objid=1, verbose=verbose)
            wlan1.setposition(x=80,y=50)
            print 'Basic Range Model default values: %s'%(str(BasicRangeModel.getdefaultvalues()))
            model_cfg = list(BasicRangeModel.getdefaultvalues())
            model_cfg[0] = str(tx_range) #Similar to default effective emane range.
            model_cfg[1] = '11000' #Similar to default emane bandwidth.
            print 'Basic Range configured values: %s'%(str(model_cfg))
            wlan1.setmodel(BasicRangeModel, tuple(model_cfg))
        else:
            raise Exception("Unknown model/distributed: %s/%s"%(model,str(distributed)))


        if not add_to_server(session): 
            print 'Could not add to server'

        if params['iperf']:
            #run_basic_iperf_test(session, wlan1, mob, trace_file, tx_range, params)
            run_multi_source_iperf_test(session, wlan1, mob, trace_file, tx_range, params)
            return

        #Copy appropriate mapping constraints.
        exp_results_dir = '%s/log/%s'%(script_dir, time_str)
        session_constraints = '%s/session%dsMappingRecord.txt'%(exp_results_dir, exp_session)
        if os.path.exists(session_constraints):
            shutil.copy(session_constraints, '%s/mappingRecordIn.txt'%session.sessiondir)
        elif params['constraints']:
            session_constraints = '%s/static/%s'%(script_dir, params['constraints'])
            if not os.path.exists(session_constraints):
                raise Exception("Could not find sessions constraints: %s"%session_constraints)
            shutil.copy(session_constraints, '%s/mappingRecordIn.txt'%session.sessiondir)

        #TODO: Do I actually need the default route/multicast service?
        services_str = "IPForward|DefaultRoute|DefaultMulticastRoute|SSH"
        master_services = services_str
        if params['dstat']: master_services += "|dstat" 
 
        master = create_node(2, session, "%s|MeanderMaster"%master_services, wlan1,
                gen_grid_position(2+params['nodes'], params['nodes'] - 1), addinf=False, verbose=verbose)

        services_str += "|%s"%params['net-routing']
        if params['quagga']: services_str += "|zebra|vtysh"
        if params['emanestats']: services_str += "|EmaneStats"
        workers = []
        num_workers = get_num_workers(k, params)
        print 'num_workers=', num_workers
        placements = get_initial_placements(params['placement'], mob)
        print 'Initial placements=',placements
        if placements: 
            create_static_routes(placements, tx_range, session.sessiondir)

        print 'Creating workers.'
        for i in range(3,3+len(num_workers)):
            if placements:
                pos = placements[i]
            else:
                pos = gen_grid_position(i, params['nodes']-1)
            worker_services = "|".join(["MeanderWorker%d"%lwid for lwid in range(1, num_workers[i-3]+1)])
            if params['pcap']: worker_services += "|PcapSrc"
            #if params['emanestats']: worker_services += "|EmaneStats"
            workers.append(create_node(i, session, "%s|%s"%(services_str, worker_services), wlan1, pos, verbose=verbose)) 
       
        routers = []
        print 'Creating routers.'
        # Create auxiliary 'router' nodes if any left
        for i in range(3+len(num_workers), 1+params['nodes']):
            if placements:
                pos = placements[i]
            else:
                pos = gen_grid_position(i, params['nodes']-1)

            if distributed:
                routers.append(create_remote_node(i, session, slave, "%s"%services_str, wlan1, pos, verbose=verbose))
            else:
                routers.append(create_node(i, session, "%s"%services_str, wlan1, pos, verbose=verbose))

        if trace_file:
            #node_map = create_node_map(range(0,6), workers)
            node_map = create_node_map(range(0,params['nodes']-2), workers+routers)
            print 'Node map=%s'%node_map
            mobility_params[4] = ('map', node_map)
            mobility_params[0] = ('file','%s/%s'%(session.sessiondir, trace_file))
            refresh_ms = int(params.get('refresh_ms', 1000))
            mobility_params[1] = ('refresh_ms', refresh_ms)
            mobility_params[2] = ('loop', 0)
            session.mobility.setconfig_keyvalues(wlan1.objid, 'ns2script', mobility_params)

        datacollect_hook = create_datacollect_hook(time_str, k, mob, exp_session) 
        session.sethook("hook:5","datacollect.sh",None,datacollect_hook)
        session.node_count="%d"%(params['nodes'])

        if params['saveconfig']:
            print 'Saving session config.'
            savesessionxml(session, '%s/session.xml'%session.sessiondir, '1.0')

        print 'Instantiating session ',exp_session, ' with CORE session id', session.sessionid
        session.instantiate()
	#remote_instantiate(session)

        chmod_dir(session.sessiondir)
        for n in range(2,3+len(num_workers)):
            node_dir = '%s/n%d.conf'%(session.sessiondir,n)
            chmod_dir('%s/var.run/sshd'%node_dir, 0655)
            chmod_dir('%s/var.run.sshd'%node_dir, 0655)
            while not os.path.exists('%s/etc.ssh/ssh_host_rsa_key'%node_dir):
                time.sleep(1)
            os.chmod('%s/etc.ssh/ssh_host_rsa_key'%node_dir, 0700)

        print 'Waiting for a meander worker/master to terminate'
        watch_meander_services(session.sessiondir, map(lambda n: "n%d"%n,
            range(2,3 + sum(num_workers))))


        if model == "Emane" and params['emanestats']:
            record_emanesh_tables(session.sessiondir, range(3, params['nodes']+1), params)

        #time.sleep(30)
        print 'Collecting data'
        session.datacollect()
	#remote_datacollect(session)
        time.sleep(5)
        print 'Shutting down'

    finally:
        print 'Shutting down session.'
        if session:
            if distributed: remote_shutdown(session)
            session.shutdown()
            if 'server' in globals():
                print 'Removing session from core daemon server'
                server.delsession(session)

        if params['sinkDisplay'] and sink_display:
            print 'Shutting down query sink display ', sink_display
            sink_display.stdin.close()
            sink_display.terminate()


def remote_configure(session, slave, slaveip):
	session.broker.addserver(slave, slaveip, coreapi.CORE_API_PORT)
	session.broker.setupserver(slave)
	session.setstate(coreapi.CORE_EVENT_CONFIGURATION_STATE)
	tlvdata = coreapi.CoreEventTlv.pack(coreapi.CORE_TLV_EVENT_TYPE,
					coreapi.CORE_EVENT_CONFIGURATION_STATE)
	session.broker.handlerawmsg(coreapi.CoreEventMessage.pack(0, tlvdata))

def remote_instantiate(session):
	tlvdata = coreapi.CoreEventTlv.pack(coreapi.CORE_TLV_EVENT_TYPE,
					coreapi.CORE_EVENT_INSTANTIATION_STATE)
	msg = coreapi.CoreEventMessage.pack(0, tlvdata)
	session.broker.handlerawmsg(msg)

def remote_shutdown(session):
	tlvdata = coreapi.CoreEventTlv.pack(coreapi.CORE_TLV_EVENT_TYPE,
					coreapi.CORE_EVENT_SHUTDOWN_STATE)
	msg = coreapi.CoreEventMessage.pack(0, tlvdata)
	session.broker.handlerawmsg(msg)

def raw_emane_global_conf_msg(wlanid):
	"""
	tlvdata = ""
	tlvdata += coreapi.CoreConfTlv.pack(coreapi.CORE_TLV_CONF_OBJ, "emane")
        tlvdata += coreapi.CoreConfTlv.pack(coreapi.CORE_TLV_CONF_NODE, wlanid)
        tlvdata += coreapi.CoreConfTlv.pack(coreapi.CORE_TLV_CONF_DATA_TYPES,
                                            (coreapi.CONF_DATA_TYPE_STRING,))
        #datatypes = tuple( map(lambda v: coreapi.CONF_DATA_TYPE_STRING,
        #                       EmaneGlobalModel.getnames()) )
	defaults = EmaneGlobalModel.getdefaultvalues()
	names = EmaneGlobalModel.getnames()
	vals = [ location.refxyz[0], location.refxyz[1], location.refgeo[0], location.refgeo[1], location.refgeo[2], location.refscale ]
	vals = "|".join(map(str, vals))
        tlvdata += coreapi.CoreConfTlv.pack(coreapi.CORE_TLV_CONF_VALUES, vals)
        msg = coreapi.CoreConfMessage.pack(0, tlvdata)
	return msg
	"""
	global_names = EmaneGlobalModel.getnames()
	global_values = list(EmaneGlobalModel.getdefaultvalues())
	global_values[ global_names.index('otamanagerdevice') ] = 'ctrl1'
	global_values[ global_names.index('otamanagergroup') ] = '224.1.2.9:45702'
	msg = EmaneGlobalModel.toconfmsg(flags=0, nodenum=wlanid,
				     typeflags=coreapi.CONF_TYPE_FLAGS_NONE, values=global_values)
	print 'Created emane conf msg=%s'%str(msg)
	return msg

def to_msg(raw_msg):
        hdr = raw_msg[:coreapi.CoreMessage.hdrsiz]
        msgtype, flags, msglen = coreapi.CoreMessage.unpackhdr(hdr)
        msgcls = coreapi.msg_class(msgtype)
        return msgcls(flags, hdr, raw_msg[coreapi.CoreMessage.hdrsiz:])

def location_conf_msg(location):
	tlvdata = ""
	tlvdata += coreapi.CoreConfTlv.pack(coreapi.CORE_TLV_CONF_OBJ, "location")
        tlvdata += coreapi.CoreConfTlv.pack(coreapi.CORE_TLV_CONF_TYPE, coreapi.CONF_TYPE_FLAGS_NONE)
        tlvdata += coreapi.CoreConfTlv.pack(coreapi.CORE_TLV_CONF_DATA_TYPES,
                                            (coreapi.CONF_DATA_TYPE_STRING,))
	vals = [ location.refxyz[0], location.refxyz[1], location.refgeo[0], location.refgeo[1], location.refgeo[2], location.refscale ]
	vals = "|".join(map(str, vals))
        tlvdata += coreapi.CoreConfTlv.pack(coreapi.CORE_TLV_CONF_VALUES, vals)
        msg = coreapi.CoreConfMessage.pack(0, tlvdata)
	return msg

def get_num_workers(k, params):
    q = params['query']
    sink_scale_factor = k if params['pyScaleOutSinks'] else 1
    if q == 'chain' or q == 'fr' or q == 'join': 
        num_workers = [1] * (1 + sink_scale_factor + (k * params['h']))
        if params['query'] == 'join':
            if params['h'] != 1: raise Exception('Only support query of height 1 for join')
            num_workers.append(1)
    elif q == 'debsGC13':
        if k > 2 or h > 1: raise Exception('Only support replication factors <= 2 for debs_gc_13') 
        num_workers = [1] * 23 
        if h > 0:
            num_workers[1] += 1 #A RB
            num_workers[5] += 1 #A RM
            num_workers[9] += 1 #A RF
            num_workers[12] += 1 #B RB
            num_workers[16] += 1 #B RM
            num_workers[20] += 1 #B RF
            if k > 1:
                num_workers[2] += 1 #A RCB
                num_workers[6] += 1 #A RCM
                num_workers[10] += 1 #A LF
                num_workers[13] += 1 #B RCB
                num_workers[17] += 1 #B RCM
                num_workers[21] += 1 #B LF

    elif q == 'nameAssist':
        num_workers = [2]+([1]*(1+ (k*3))) 
    elif q == 'heatMap':
        sources =  int(params['sources'])
        sinks = int(params['sinks'])
        fan_in = int(params['fanin'])
        height = int(math.ceil(math.log(sources, fan_in)))
        children = sources
        join_ops = 0 
        for i in range(0, height):
            parents = children / fan_in
            if children % fan_in > 0: parents += 1
            join_ops += parents
            children = parents
        print 'height=%d, join_ops=%d'%(height, join_ops)
        worker_nodes = params['nodes'] - 2
        if worker_nodes >= sources + k*(join_ops) + (sink_scale_factor * sinks):
            num_workers = [1] * (sources + k*(join_ops) + (sink_scale_factor * sinks))
        else:
            #Need to have multiple workers on some nodes.
            #N.B. Don't want to colocate replicas of the
            #same operator or the source.
            raise Exception("TODO")
    else: 
        raise Exception('Unknown query type: %s'%q)

    return num_workers

def create_node(i, session, services_str, wlan, pos, ip_offset=-1, addinf=True, verbose=False):
#def create_node(i, session, services_str, wlan, pos, ip_offset=8):
    tstart = time.time() 
    n = session.addobj(cls = pycore.nodes.CoreNode, name="n%d"%i, objid=i)
    taddobj = time.time() - tstart
    #n.setposition(x=pos[0], y=pos[1], z=1.0) N.B. Might need an altitude of 3.0 for 2ray!
    print('N.B. Might need an altitude of 3.0 for 2ray (z=1.0)')
    n.setposition(x=pos[0], y=pos[1])
    session.services.addservicestonode(n, "", services_str, verbose=verbose)
    taddservices = time.time() - tstart
    if addinf:
        ip = i + ip_offset 
        #n.newnetif(net=wlan, addrlist=["10.0.0.%d/32"%(ip)], ifindex=0)
        n.newnetif(net=wlan, addrlist=["10.0.0.%d/24"%(ip)], ifindex=0)
        taddnetif = time.time() - tstart
        n.cmd([SYSCTL_BIN, "net.ipv4.icmp_echo_ignore_broadcasts=0"])
        tcmd = time.time() - tstart
        print 'taddobj=%.3f,taddserv=%.3f,taddnet=%.3f,tcmd=%.3f'%(taddobj,taddservices,taddnetif,tcmd)
        print 'Created node n%d (10.0.0.%d) with initial pos=(%.1f,%.1f)'%(i,ip,pos[0], pos[1])
    else:
        print 'Created node n%d (no inf) with initial pos=(%.1f,%.1f)'%(i,pos[0], pos[1])
    #n.cmd([SYSCTL_BIN, "net.ipv4.ip_forward=1"])
    #n.cmd([SYSCTL_BIN, "net.ipv4.conf.all.forwarding=1"])
    #n.cmd([SYSCTL_BIN, "net.ipv6.conf.all.forwarding=1"])
    #n.cmd([SYSCTL_BIN, "net.ipv4.conf.all.rp_filter=0"])
    #n.cmd([SYSCTL_BIN, "net.ipv4.conf.default.rp_filter=0"])

    return n

def create_remote_node(i, session, slave, services_str, wlan, pos, ip_offset=-1, addinf=True, verbose=False):
        n = pycore.nodes.CoreNode(session = session, objid = i,
                                    name = "n%d" % i, start=False)
        print('N.B. Might need an altitude of 3.0 for 2ray (z=1.0)')
        #n.setposition(x=pos[0], y=pos[1], z=1.0) N.B. Might need an altitude of 3.0 for 2ray!
        n.setposition(x=pos[0],y=pos[1])
        n.server = slave
        session.services.addservicestonode(n, "", services_str, verbose=verbose)
	# TODO: addinf
        session.broker.handlerawmsg(n.tonodemsg(flags=coreapi.CORE_API_ADD_FLAG | coreapi.CORE_API_STR_FLAG))

	#if addinf:
	#	ip = i + ip_offset 
	#	n.newnetif(net=wlan, addrlist=["10.0.0.%d/32"%(ip)], ifindex=0)
	prefix = ipaddr.IPv4Prefix("10.0.0.0/24")
	ip = i + ip_offset 
        tlvdata = coreapi.CoreLinkTlv.pack(coreapi.CORE_TLV_LINK_N1NUMBER,
                                           wlan.objid)
        tlvdata += coreapi.CoreLinkTlv.pack(coreapi.CORE_TLV_LINK_N2NUMBER, i)
        tlvdata += coreapi.CoreLinkTlv.pack(coreapi.CORE_TLV_LINK_TYPE,
                                            coreapi.CORE_LINK_WIRED)
        tlvdata += coreapi.CoreLinkTlv.pack(coreapi.CORE_TLV_LINK_IF2NUM, 0)
        tlvdata += coreapi.CoreLinkTlv.pack(coreapi.CORE_TLV_LINK_IF2IP4,
                                            prefix.addr(i+ip_offset))
                                            #"10.0.0.%d"%ip)
        tlvdata += coreapi.CoreLinkTlv.pack(coreapi.CORE_TLV_LINK_IF2IP4MASK,
                                            prefix.prefixlen)
        msg = coreapi.CoreLinkMessage.pack(coreapi.CORE_API_ADD_FLAG, tlvdata)
        session.broker.handlerawmsg(msg)

def create_node_map(ns_nums, nodes):
    print 'ns_nums=%s'%str(ns_nums)
    print 'nodes=%s'%str(nodes)
    if len(ns_nums) != len(nodes): 
        raise Exception("Invalid node mapping, %d != %d"%(len(ns_nums), len(nodes)))
    return ",".join(map(lambda (ns_num, node) : "%d:%d"%(ns_num,node.objid), zip(ns_nums, nodes)))

def get_initial_placements(placements, mobility):
    if not placements or mobility > 0.0:
        return None
    else:
        result = {}
        placements_path = '%s/static/%s'%(script_dir, placements)
        with open(placements_path, 'r') as pf:
            for line in pf:
                els = map(int, line.split(','))
                result[els[0]] = (els[1], els[2])

        return result

def gen_linear_position(i):
    return (50 * i, 100)

def gen_grid_position(i, nodes, offset=3, spacing=400):
    if i < offset: raise Exception("Invalid offset for %d: %d"%(i,offset))
    dim = max(1.0, math.floor(math.sqrt(nodes)))
    num_x = (i-offset) % dim 
    num_y = math.floor((i-offset) / dim)
    print 'i=',i, 'nodes=',nodes,'offset=',offset,'dim=',dim,'num_x=',num_x,'num_y=',num_y
    return (int(spacing * num_x), int(spacing * num_y)) 

def add_to_server(session):
    global server
    try:
        server.addsession(session)
        return True
    except NameError:
        print 'Name error'
        return False

def create_datacollect_hook(time_str, k, mob, exp_session):
    print 'Script dir = %s'%script_dir
    return datacollect_template%(script_dir, time_str, k, mob, exp_session)

def watch_meander_services(sessiondir, node_names):
    while True:
        for name in node_names:
            for process in ['worker1', 'worker2', 'master']:
                if os.path.exists("%s/%s.conf/%s.shutdown"%(sessiondir, name, process)):
		    print 'Shutdown file exists for node %s (%s) - exiting'%(name, process)
		    return

        time.sleep(0.5)

def write_replication_factor(k, session_dir):
    with open('%s/k.txt'%session_dir, 'w') as f:
        f.write(str(k))

def write_chain_length(h, session_dir):
    with open('%s/h.txt'%session_dir, 'w') as f:
        f.write(str(h))

def write_query_type(query, session_dir):
    with open('%s/query.txt'%session_dir, 'w') as f:
        f.write(str(query))

def write_extra_params(params, session_dir):
    with open('%s/extra_params.txt'%session_dir, 'w') as f:
        f.write('sources=%s\n'%str(params['sources']))
        f.write('sinks=%s\n'%str(params['sinks']))
        f.write('fanin=%s\n'%str(params['fanin']))

def write_session_params(params, session_dir):
    with open('%s/session_params.txt'%session_dir, 'w') as f:
        for k in params:
            f.write('%s=%s\n'%(k,str(params[k])))

def copy_seep_jar(session_dir):
    dest = '%s/lib'%session_dir
    os.mkdir(dest)
    shutil.copy('%s/../lib/%s'%(script_dir,seep_jar), dest)

#def exists_mobility_trace(time_str, session):
#    return os.path.isfile(

def regen_sessions(time_str):
    raise Exception("TODO")

def run_basic_iperf_test(session, wlan1, mob, trace_file, tx_range, params):
    services_str = "IPForward|SSH"
    services_str += "|%s"%params['net-routing']
    if params['quagga']: services_str += "|zebra|vtysh"
    if params['emanestats']: services_str += "|EmaneStats"
    if params['dstat']: services_str += "|dstat"

    placements = get_initial_placements(params['placement'], mob)
    if placements: create_static_routes(placements, tx_range, session.sessiondir)
    print placements
    pos = placements[2] if placements else gen_grid_position(2, params['nodes']-1, 2, spacing=400)
    src_services = "%s|IPerfSrc"%(services_str)
    if params['duplex']: src_services += "|IPerfSink"
    src = create_node(2, session, src_services, wlan1, pos) 
    
    pos = placements[3] if placements else gen_grid_position(3, params['nodes']-1, 2, spacing=400)
    sink_services = "%s|IPerfSink"%(services_str)
    if params['duplex']: sink_services += "|IPerfSrc"
    sink = create_node(3, session, sink_services, wlan1, pos) 
   
    routers = []
    print 'Creating routers.'
    # Create auxiliary 'router' nodes if any left
    for i in range(4, params['nodes']+1):
        if placements:
            pos = placements[i]
        else:
            pos = gen_grid_position(i, params['nodes']-1, 2, spacing=400)
        routers.append(create_node(i, session, "%s"%services_str, wlan1, pos))

    if trace_file:
        #node_map = create_node_map(range(0,6), workers)
        node_map = create_node_map(range(0,params['nodes']-1), [src, sink]+routers)
        print 'Node map=%s'%node_map
        mobility_params[4] = ('map', node_map)
        mobility_params[0] = ('file','%s/%s'%(session.sessiondir, trace_file))
        refresh_ms = int(params.get('refresh_ms', 1000))
        mobility_params[1] = ('refresh_ms', refresh_ms)
        session.mobility.setconfig_keyvalues(wlan1.objid, 'ns2script', mobility_params)


    session.node_count="%d"%(params['nodes'])
    session.instantiate()
    time.sleep(1000000)

def run_multi_source_iperf_test(session, wlan1, mob, trace_file, tx_range, params):
    services_str = "IPForward|SSH"
    services_str += "|%s"%params['net-routing']
    if params['quagga']: services_str += "|zebra|vtysh"
    if params['emanestats']: services_str += "|EmaneStats"
    if params['dstat']: services_str += "|dstat"

    placements = get_initial_placements(params['placement'], mob)
    if placements: create_static_routes(placements, tx_range, session.sessiondir)

    iperf_cxns = read_iperf_cxns(params)
    copy_iperf_cxns(session.sessiondir, params)

    for i in range(2, params['nodes']+1):
        i_services_str = services_str
        # Check if src or sink			
        if has_iperf_src(i, iperf_cxns):
            i_services_str += "|IPerfSrc"
        if has_iperf_dest(i, iperf_cxns):
            i_services_str += "|IPerfSink"

        if placements:
            pos = placements[i]
        else:
            raise Exception("TODO")

        create_node(i, session, "%s"%i_services_str, wlan1, pos)
		
    print placements

    session.node_count="%d"%(params['nodes'])
    session.instantiate()
    time.sleep(1000000)

def read_iperf_cxns(params):
    cxns = []
    with open("%s/static/%s"%(script_dir, params['iperfcxns']), 'r') as cxns_file:
        for line in cxns_file:
            cxns.append(line.split(","))

    return cxns 

def copy_iperf_cxns(sessiondir, params):
    shutil.copy("%s/static/%s"%(script_dir, params['iperfcxns']), '%s/iperf_connections.txt'%sessiondir)

def has_iperf_src(node, cxns):
    for [src, dest] in cxns:
        if int(src) == int(node):
            return True

def has_iperf_dest(node, cxns):
    for [src, dest] in cxns:
        if int(dest) == int(node):
            return True

def start_query_sink_display(logfile, logdir, params):

    if params['query'] == 'fr':   
        args = ['java', 'FaceRecognitionDemo']
        cwd = script_dir 
    elif params['query'] == 'heatMap':   
        args = ['java', 'AcitaDemo']
        cwd = script_dir + '/heatMap'
    else: return None

    #os.mkdir(logdir)
    with open(logdir + "/" + logfile, 'w') as log:
        p = subprocess.Popen(args, stdout=log, cwd=cwd, stderr=subprocess.STDOUT, env=os.environ.copy())

    return p

def get_repo_dir():
    regex = re.compile('(.*)/seep-system')
    return re.search(regex, script_dir).groups()[0]

def record_emanesh_tables(sessiondir, nodes, params):
    for node in nodes:
        args = ['/usr/sbin/vcmd', '-c', '%s/n%d'%(sessiondir, node), './record-emane-tables.sh', str(node)]
        with open('%s/n%d.conf/emane-tables-n%d.txt'%(sessiondir, node, node), 'w') as log:
            p = subprocess.Popen(args, stdout=log, cwd='%s/n%d.conf'%(sessiondir,node), stderr=subprocess.STDOUT, env=os.environ.copy())
            p.wait()

if __name__ == "__main__" or __name__ == "__builtin__":
    print 'Hello world'
    parser = argparse.ArgumentParser(description='Run several meander experiments on CORE')
    parser.add_argument('--k', dest='k', default='2', help='replication factors (2)')
    parser.add_argument('--h', dest='h', default='2', help='chain length (2)')
    parser.add_argument('--x', dest='x', default='1200', help='Grid x dimension (1200)')
    parser.add_argument('--y', dest='y', default='1200', help='Grid y dimension (1200)')
    parser.add_argument('--duration', dest='duration', default='100000', help='Mobility params duration')
    parser.add_argument('--query', dest='query', default='chain', help='query type: (chain), join')
    parser.add_argument('--sources', dest='sources', default='1', help='Sources')
    parser.add_argument('--sinks', dest='sinks', default='1', help='Sinks (non-replicated)')
    parser.add_argument('--fanin', dest='fanin', default='2', help='Join fan-in')
    parser.add_argument('--pausetime', dest='pt', default='5.0', help='pause time (5.0)')
    parser.add_argument('--sessions', dest='sessions', default='1', help='number of sessions to run')
    parser.add_argument('--specific', dest='specific', default=False, action='store_true', help='Run a specific session')
    parser.add_argument('--plotOnly', dest='plot_time_str', default=None, help='time_str of run to plot (hh-mm-DDDddmmyy)[None]')
    parser.add_argument('--nodes', dest='nodes', default='10', help='Total number of core nodes in network')
    parser.add_argument('--model', dest='model', default="Emane", help='Wireless model (Basic, Emane)')
    parser.add_argument('--routing', dest='routing', default='OLSRETX',
            help='Net layer routing alg (OLSR, OLSRETX, OSPFv3MDR)')
    parser.add_argument('--preserve', dest='preserve', default=False, action='store_true', help='Preserve session directories')
    parser.add_argument('--saveconfig', dest='saveconfig', default=False, action='store_true', help='Export the session configuration to an XML file')
    parser.add_argument('--constraints', dest='constraints', default='', help='Export the session configuration to an XML file')
    parser.add_argument('--placement', dest='placement', default='', help='Explicit static topology to use for all sessions')
    parser.add_argument('--iperf', dest='iperf', default=False, action='store_true', help='Do an iperf test')
    parser.add_argument('--iperfcxns', dest='iperfcxns', default=None, help='Do an iperf test')
    parser.add_argument('--scaleSinks', dest='scale_sinks', default=False, action='store_true', help='Replicate sinks k times')
    parser.add_argument('--quagga', dest='quagga', default=False, action='store_true', help='Start quagga services (zebra, vtysh)')
    parser.add_argument('--pcap', dest='pcap', default=False, action='store_true', help='Start pcap service for workers.')
    parser.add_argument('--emanestats', dest='emanestats', default=False, action='store_true', help='Start emanestats service on master')
    parser.add_argument('--dstat', dest='dstat', default=False, action='store_true', help='Start dstat service on master.')
    parser.add_argument('--duplex', dest='duplex', default=False, action='store_true', help='Send in both directions for iperf tests')
    parser.add_argument('--verbose', dest='verbose', action='store_true', default=False, help='Verbose core logging')
    parser.add_argument('--sinkDisplay', dest='sink_display', default=False, action='store_true', help='Start a sink display for query output')
    parser.add_argument('--gui', dest='gui', default=False, action='store_true', help='Show placements in core GUI')
    parser.add_argument('--slave', dest='slave', default=None, help='Hostname of slave')
    parser.add_argument('--emaneMobility', dest='emane_mobility', default=False, action='store_true', help='Use emane location events for mobility (instead of ns2)')
    args=parser.parse_args()

    k=int(args.k)
    pt=float(args.pt)
    params = {'nodes':int(args.nodes)}
    if args.model: params['model']=args.model
    params['net-routing']=args.routing
    params['specific']=args.specific
    params['preserve']=args.preserve
    params['h']=int(args.h)
    params['x']=int(args.x)
    params['y']=int(args.y)
    params['duration']=args.duration
    params['query']=args.query
    params['saveconfig']=args.saveconfig
    params['constraints']=args.constraints
    params['placement']=args.placement
    params['sources']=args.sources
    params['sinks']=args.sinks
    params['fanin']=args.fanin
    params['iperf']=args.iperf
    params['iperfcxns']=args.iperfcxns
    params['pyScaleOutSinks']=args.scale_sinks
    params['scaleOutSinks']=pybool_to_javastr(args.scale_sinks)
    params['quagga']=args.quagga
    params['pcap']=args.pcap
    params['emanestats']=args.emanestats
    params['dstat']=args.dstat
    params['duplex']=args.duplex
    params['sinkDisplay']=args.sink_display
    params['enableSinkDisplay']=pybool_to_javastr(args.sink_display)
    params['enableGUI']= "true" if args.gui else "false"
    params['slave']= args.slave 
    params['verbose']= args.verbose 
    params['emaneMobility']= args.emane_mobility
	
    #if args.verbose: params['verbose']='true'

    sessions = int(args.sessions)
    session_ids = [sessions] if args.specific else range(0,sessions)
    if args.plot_time_str:
        time_str = args.plot_time_str
        regen_sessions(time_str)
    else:
        time_str = time.strftime('%H-%M-%S-%a%d%m%y')
        run_sessions(time_str, k, pt, session_ids,params)
