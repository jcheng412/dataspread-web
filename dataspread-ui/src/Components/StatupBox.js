import React, {Component} from 'react'
import { Button, Divider, Segment } from 'semantic-ui-react'
import ModalOpenFile from './Menu/load'
import ModalImportFile from './Menu/import'
import ModalNewFile from './Menu/new'


export default class StartupBox extends Component {

  render() {
      return (
        <div style={center_screen}>
            <Segment padded>
                <ModalNewFile onSelectFile={this.props.onSelectFile}/>

                <Divider horizontal>Or</Divider>

                <ModalOpenFile inMenu={false} onSelectFile={this.props.onSelectFile}/>
            
                <Divider horizontal>Or</Divider>

                <ModalImportFile inMenu={false} onSelectFile={this.props.onSelectFile}/>
            
            </Segment>
        </div>
    )
  }
}


const center_screen = {
    'display': 'flex',
    'flexDirection': 'column',
    'justifyContent': 'center',
    'alignItems': 'center',
    'textAlign': 'center',
    'minHeight': '100vh',
};
  