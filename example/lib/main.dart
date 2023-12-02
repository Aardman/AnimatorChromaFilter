import 'package:flutter/material.dart';
import 'package:flutter/services.dart'; 
import 'package:animatorfilter_example/preview_host_page.dart';
  

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp, DeviceOrientation.landscapeLeft, DeviceOrientation.portraitDown, DeviceOrientation.landscapeRight]);

  runApp(const FilterExample());
}

class  FilterExample extends  StatelessWidget {
  const FilterExample({Key? key}) : super(key:key);

  @override
  Widget build(BuildContext context){
    return const MaterialApp(
      title:"Preview Demo",
      home: HomeWidget());
  }

}

class HomeWidget extends StatelessWidget {
  const HomeWidget({Key? key}) : super(key:key);

  @override 
  Widget build(BuildContext context){
     return const PreviewPage();
  }
}